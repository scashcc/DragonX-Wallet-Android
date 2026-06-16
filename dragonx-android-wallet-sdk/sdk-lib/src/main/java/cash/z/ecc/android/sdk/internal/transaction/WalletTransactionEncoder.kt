package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.db.entity.EncodedTransaction
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.ext.masked
import cash.z.ecc.android.sdk.internal.SaplingParamTool
import cash.z.ecc.android.sdk.internal.twig
import cash.z.ecc.android.sdk.internal.twigTask
import cash.z.ecc.android.sdk.jni.RustBackend
import cash.z.ecc.android.sdk.jni.RustBackendWelding
import cash.z.ecc.android.sdk.model.Zatoshi
import kotlinx.coroutines.delay

/**
 * Class responsible for encoding a transaction in a consistent way. This bridges the gap by
 * behaving like a stateless API so that callers can request [createTransaction] and receive a
 * result, even though there are intermediate database interactions.
 *
 * @property rustBackend the instance of RustBackendWelding to use for creating and validating.
 * @property repository the repository that stores information about the transactions being created
 * such as the raw bytes and raw txId.
 */
internal class WalletTransactionEncoder(
    private val rustBackend: RustBackendWelding,
    private val repository: TransactionRepository
) : TransactionEncoder {

    /**
     * Creates a transaction, throwing an exception whenever things are missing. When the provided
     * wallet implementation doesn't throw an exception, we wrap the issue into a descriptive
     * exception ourselves (rather than using double-bangs for things).
     *
     * @param spendingKey the key associated with the notes that will be spent.
     * @param amount the amount of zatoshi to send.
     * @param toAddress the recipient's address.
     * @param memo the optional memo to include as part of the transaction.
     * @param fromAccountIndex the optional account id to use. By default, the 1st account is used.
     *
     * @return the successfully encoded transaction or an exception
     */
    override suspend fun createTransaction(
        spendingKey: String,
        amount: Zatoshi,
        toAddress: String,
        memo: ByteArray?,
        fromAccountIndex: Int
    ): EncodedTransaction {
        val transactionId = createSpend(spendingKey, amount, toAddress, memo)
        return awaitEncodedTransaction(transactionId)
            ?: throw TransactionEncoderException.TransactionNotFoundException(transactionId)
    }

    override suspend fun createShieldingTransaction(
        spendingKey: String,
        transparentSecretKey: String,
        memo: ByteArray?
    ): EncodedTransaction {
        val transactionId = createShieldingSpend(spendingKey, transparentSecretKey, memo)
        return awaitEncodedTransaction(transactionId)
            ?: throw TransactionEncoderException.TransactionNotFoundException(transactionId)
    }

    override suspend fun createConsolidationTransaction(
        spendingKey: String,
        maxInputs: Int,
        fromAccountIndex: Int
    ): EncodedTransaction? {
        val transactionId = createConsolidationSpend(spendingKey, maxInputs, fromAccountIndex)
        if (transactionId == RustBackendWelding.NOTHING_TO_CONSOLIDATE) {
            return null
        }
        if (transactionId == RustBackendWelding.NEEDS_RESCAN) {
            // Funds exist but their witnesses are missing at the anchor; consolidation can't help
            // until a full rescan rebuilds them. Surface this distinctly so the UI offers a rescan
            // instead of falsely reporting "nothing to consolidate".
            throw TransactionEncoderException.ConsolidationNeedsRescanException
        }
        return awaitEncodedTransaction(transactionId)
            ?: throw TransactionEncoderException.TransactionNotFoundException(transactionId)
    }

    /**
     * Look up the just-created transaction by its row id, retrying briefly if it is not yet visible.
     *
     * The Rust backend writes the transaction (raw bytes included) and COMMITs it on its OWN sqlite
     * connection before returning [transactionId]; a positive id therefore means the row exists and
     * was committed (the backend returns -1 on any failure). However, this read goes through Room's
     * SEPARATE connection, which can momentarily not yet see that freshly-committed row (a classic
     * cross-connection read-after-write race, more likely while a heavy scan is hammering the db).
     * Previously that race surfaced as the scary "Unable to find transactionId N in the repository"
     * failure even though the spend was fine — and worse, the input notes had already been marked
     * spent locally by store_sent_tx, so the balance looked stuck. A short bounded retry lets Room
     * catch up so the send proceeds (or, if the row truly never materializes, we still throw below
     * and never broadcast, leaving funds untouched).
     */
    private suspend fun awaitEncodedTransaction(transactionId: Long): EncodedTransaction? {
        // ~0.1 + 0.2 + 0.4 + 0.8 + 1.6 = 3.1s worst case; trivial next to the ~10s proof time.
        var delayMs = 100L
        repeat(6) { attempt ->
            repository.findEncodedTransactionById(transactionId)?.let { return it }
            twig("encoded tx $transactionId not visible yet (attempt ${attempt + 1}); retrying in ${delayMs}ms")
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(1600L)
        }
        return repository.findEncodedTransactionById(transactionId)
    }

    /**
     * Utility function to help with validation. This is not called during [createTransaction]
     * because this class asserts that all validation is done externally by the UI, for now.
     *
     * @param address the address to validate
     *
     * @return true when the given address is a valid z-addr
     */
    override suspend fun isValidShieldedAddress(address: String): Boolean =
        rustBackend.isValidShieldedAddr(address)

    /**
     * Utility function to help with validation. This is not called during [createTransaction]
     * because this class asserts that all validation is done externally by the UI, for now.
     *
     * @param address the address to validate
     *
     * @return true when the given address is a valid t-addr
     */
    override suspend fun isValidTransparentAddress(address: String): Boolean =
        rustBackend.isValidTransparentAddr(address)

    override suspend fun getConsensusBranchId(): Long {
        val height = repository.lastScannedHeight()
        if (height < rustBackend.network.saplingActivationHeight) {
            throw TransactionEncoderException.IncompleteScanException(height)
        }
        return rustBackend.getBranchIdForHeight(height)
    }

    /**
     * Does the proofs and processing required to create a transaction to spend funds and inserts
     * the result in the database. On average, this call takes over 10 seconds.
     *
     * @param spendingKey the key associated with the notes that will be spent.
     * @param amount the amount of zatoshi to send.
     * @param toAddress the recipient's address.
     * @param memo the optional memo to include as part of the transaction.
     * @param fromAccountIndex the optional account id to use. By default, the 1st account is used.
     *
     * @return the row id in the transactions table that contains the spend transaction or -1 if it
     * failed.
     */
    private suspend fun createSpend(
        spendingKey: String,
        amount: Zatoshi,
        toAddress: String,
        memo: ByteArray? = byteArrayOf(),
        fromAccountIndex: Int = 0
    ): Long {
        return twigTask(
            "creating transaction to spend $amount zatoshi to" +
                " ${toAddress.masked()} with memo $memo"
        ) {
            try {
                val branchId = getConsensusBranchId()
                SaplingParamTool.ensureParams((rustBackend as RustBackend).pathParamsDir)
                twig("params exist! attempting to send with consensus branchId $branchId...")
                rustBackend.createToAddress(
                    branchId,
                    fromAccountIndex,
                    spendingKey,
                    toAddress,
                    amount.value,
                    memo
                )
            } catch (t: Throwable) {
                twig("${t.message}")
                throw t
            }
        }.also { result ->
            twig("result of sendToAddress: $result")
        }
    }

    private suspend fun createShieldingSpend(
        spendingKey: String,
        transparentSecretKey: String,
        memo: ByteArray? = byteArrayOf()
    ): Long {
        return twigTask("creating transaction to shield all UTXOs") {
            try {
                SaplingParamTool.ensureParams((rustBackend as RustBackend).pathParamsDir)
                twig("params exist! attempting to shield...")
                rustBackend.shieldToAddress(
                    spendingKey,
                    transparentSecretKey,
                    memo
                )
            } catch (t: Throwable) {
                // TODO: if this error matches: Insufficient balance (have 0, need 1000 including fee)
                // then consider custom error that says no UTXOs existed to shield
                twig("Shield failed due to: ${t.message}")
                throw t
            }
        }.also { result ->
            twig("result of shieldToAddress: $result")
        }
    }

    /**
     * Runs one round of small-note consolidation in the Rust backend. Returns the row id of the
     * created transaction, or [RustBackendWelding.NOTHING_TO_CONSOLIDATE] when there is nothing
     * left worth consolidating. On average each round takes several seconds (it generates a zk
     * proof per input).
     */
    private suspend fun createConsolidationSpend(
        spendingKey: String,
        maxInputs: Int,
        fromAccountIndex: Int = 0
    ): Long {
        return twigTask("creating consolidation transaction sweeping up to $maxInputs notes") {
            try {
                SaplingParamTool.ensureParams((rustBackend as RustBackend).pathParamsDir)
                twig("params exist! attempting to consolidate...")
                rustBackend.consolidateToAddress(
                    fromAccountIndex,
                    spendingKey,
                    maxInputs
                )
            } catch (t: Throwable) {
                twig("Consolidation failed due to: ${t.message}")
                throw t
            }
        }.also { result ->
            twig("result of consolidateToAddress: $result")
        }
    }
}
