package cash.z.ecc.android.ext

import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool

/**
 * Key access that supports BOTH normal (seed-based) wallets and private-key-restored wallets.
 *
 * - A seed-based wallet stores the BIP-39 [Const.Backup.SEED]; the spending key is derived from it
 *   exactly as before (no behaviour change for existing wallets).
 * - A private-key-restored wallet has no seed; the Sapling spending key (secret-extended-key-main…)
 *   is stored directly in [Const.Backup.SPENDING_KEY] and returned here.
 */
object Keys {

    /**
     * A valid (but unused) compressed secp256k1 public key — the curve generator G. Private-key
     * restore has no transparent component, but the Rust `init_accounts_table` parses the stored
     * extpub with `PublicKey::from_str(..).unwrap()`, which panics on an empty/invalid value. We
     * store this placeholder so initialization succeeds; the transparent side is never used by the
     * shielded-only flow (send/receive use the shielded z-address).
     */
    const val PLACEHOLDER_EXTPUB =
        "0279BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"

    /** The active wallet's Sapling spending key (seed-derived, or the stored private key). */
    suspend fun activeSpendingKey(network: ZcashNetwork): String {
        val box = DependenciesHolder.lockBox
        box.getBytes(Const.Backup.SEED)?.let { seed ->
            return DerivationTool.deriveSpendingKeys(seed, network)[0]
        }
        box.getCharsUtf8(Const.Backup.SPENDING_KEY)?.let { return String(it) }
        throw IllegalStateException("No spending key available (no seed and no stored private key)")
    }
}
