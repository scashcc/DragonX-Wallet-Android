package cash.z.ecc.android.ui.profile

import android.widget.Toast
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.lockbox.LockBox
import cash.z.ecc.android.sdk.Initializer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.db.entity.PendingTransaction
import cash.z.ecc.android.sdk.ext.ZcashSdk
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.tool.DerivationTool
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ProfileViewModel : ViewModel() {

    val synchronizer: Synchronizer = DependenciesHolder.synchronizer

    private val lockBox: LockBox = DependenciesHolder.lockBox

    private val prefs: LockBox = DependenciesHolder.prefs

    // TODO: track this in the app and then fetch. For now, just estimate the blocks per second.
    val bps = 40

    suspend fun getShieldedAddress(): String = synchronizer.getAddress()

    suspend fun getTransparentAddress(): String {
        return synchronizer.getTransparentAddress()
    }

    override fun onCleared() {
        super.onCleared()
        twig("ProfileViewModel cleared!")
    }

    suspend fun fetchUtxos(): Int {
        val address = getTransparentAddress()
        val height: Long = lockBox[Const.Backup.BIRTHDAY_HEIGHT]
            ?: synchronizer.network.saplingActivationHeight.value
        return synchronizer.refreshUtxos(address, BlockHeight.new(synchronizer.network, height))
            ?: 0
    }

    suspend fun getTransparentBalance(): WalletBalance {
        val address = getTransparentAddress()
        return synchronizer.getTransparentBalance(address)
    }

    fun shieldFunds(): Flow<PendingTransaction> {
        return lockBox.getBytes(Const.Backup.SEED)?.let {
            val sk = runBlocking { DerivationTool.deriveSpendingKeys(it, synchronizer.network)[0] }
            val tsk =
                runBlocking { DerivationTool.deriveTransparentSecretKey(it, synchronizer.network) }
            val addr = runBlocking {
                DerivationTool.deriveTransparentAddressFromPrivateKey(
                    tsk,
                    synchronizer.network
                )
            }
            synchronizer.shieldFunds(
                sk,
                tsk,
                "${ZcashSdk.DEFAULT_SHIELD_FUNDS_MEMO_PREFIX}\nAll UTXOs from $addr"
            ).onEach {
                twig("Received shielding txUpdate: ${it?.toString()}")
//                updateMetrics(it)
//                reportFailures(it)
            }
        } ?: throw IllegalStateException("Seed was expected but it was not found!")
    }

    fun setEasterEggTriggered() {
        lockBox.setBoolean(Const.Pref.EASTER_EGG_TRIGGERED_SHIELDING, true)
    }

    fun isEasterEggTriggered(): Boolean {
        return lockBox.getBoolean(Const.Pref.EASTER_EGG_TRIGGERED_SHIELDING)
    }

    suspend fun cancel(id: Long) {
        synchronizer.cancelSpend(id)
    }

    fun wipe() {
        synchronizer.stop()
        Toast.makeText(
            ZcashWalletApp.instance,
            "SUCCESS! Wallet data cleared. Please relaunch to rescan!",
            Toast.LENGTH_LONG
        ).show()
        runBlocking {
            Initializer.erase(
                ZcashWalletApp.instance,
                ZcashWalletApp.instance.defaultNetwork
            )
        }
    }

    suspend fun fullRescan() {
        synchronizer.latestBirthdayHeight?.let {
            rewindTo(it)
        }
    }

    suspend fun quickRescan() {
        synchronizer.latestHeight?.let {
            val newHeightValue =
                (it.value - 8064L).coerceAtLeast(synchronizer.network.saplingActivationHeight.value)
            rewindTo(BlockHeight.new(synchronizer.network, newHeightValue))
        }
    }

    private suspend fun rewindTo(targetHeight: BlockHeight) {
        twig("TMP: rewinding to targetHeight $targetHeight")
        synchronizer.rewindToNearestHeight(targetHeight, true)
    }

    fun fullScanDistance(): Long {
        synchronizer.latestHeight?.let { latestHeight ->
            synchronizer.latestBirthdayHeight?.let { latestBirthdayHeight ->
                return (latestHeight.value - latestBirthdayHeight.value).coerceAtLeast(0)
            }
        }
        return 0
    }

    fun quickScanDistance(): Int {
        val latest = synchronizer.latestHeight
        val oneWeek = 60 * 60 * 24 / 75 * 7 // a week's worth of blocks
        val height = BlockHeight.new(
            synchronizer.network,
            ((latest?.value ?: synchronizer.network.saplingActivationHeight.value) - oneWeek)
                .coerceAtLeast(synchronizer.network.saplingActivationHeight.value)
        )
        val foo = runBlocking {
            synchronizer.getNearestRewindHeight(height)
        }
        return ((latest?.value ?: 0) - foo.value).toInt().coerceAtLeast(0)
    }

    fun blocksToMinutesString(blocks: BlockHeight): String {
        val duration = (blocks.value / bps.toDouble()).toDuration(DurationUnit.SECONDS)
        return duration.toString(DurationUnit.MINUTES).replace("m", " minutes")
    }

    fun blocksToMinutesString(blocks: Int): String {
        val duration = (blocks / bps.toDouble()).toDuration(DurationUnit.SECONDS)
        return duration.toString(DurationUnit.MINUTES).replace("m", " minutes")
    }

    fun blocksToMinutesString(blocks: Long): String {
        val duration = (blocks / bps.toDouble()).toDuration(DurationUnit.SECONDS)
        return duration.toString(DurationUnit.MINUTES).replace("m", " minutes")
    }
}
