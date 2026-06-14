package cash.z.ecc.android.ui.home

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.toAppString
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.Synchronizer.Status.*
import cash.z.ecc.android.sdk.block.CompactBlockProcessor
import cash.z.ecc.android.sdk.db.entity.PendingTransaction
import cash.z.ecc.android.sdk.db.entity.isMined
import cash.z.ecc.android.sdk.db.entity.isSubmitSuccess
import cash.z.ecc.android.sdk.ext.ZcashSdk.MINERS_FEE
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt

// There are deprecations with the use of BroadcastChannel
@kotlinx.coroutines.ObsoleteCoroutinesApi
class HomeViewModel : ViewModel() {

    lateinit var uiModels: Flow<UiModel>

    /**
     * Latest UiModel, exposed as state for the Compose home screen. The Fragment collects [uiModels]
     * and pushes each emission here; the Compose screen observes it via collectAsState().
     */
    val homeState = MutableStateFlow<UiModel?>(null)

    lateinit var _typedChars: ConflatedBroadcastChannel<Char>

    var initialized = false

    fun reinitialize() {
        twig("HomeViewModel.reinitialize: rebinding to new synchronizer")
        initialized = false
        initializeMaybe()
    }

    fun initializeMaybe(preTypedChars: String = "0") {
        twig("init called")
        if (initialized) {
            twig("Warning already initialized HomeViewModel. Ignoring call to initialize.")
            return
        }

        if (::_typedChars.isInitialized) {
            _typedChars.close()
        }
        _typedChars = ConflatedBroadcastChannel()
        val typedChars = _typedChars.asFlow()
        val decimal = '.' // R.string.key_decimal.toAppString()[0]
        val backspace = R.string.key_backspace.toAppString()[0]
        val zec = typedChars.scan(preTypedChars) { acc, c ->
            when {
                // no-op cases
                acc == "0" && c == '0' ||
                    (c == backspace && acc == "0")
                    || (c == decimal && acc.contains(decimal)) -> {
                    acc
                }
                c == backspace && acc.length <= 1 -> {
                    "0"
                }
                c == backspace -> {
                    acc.substring(0, acc.length - 1)
                }
                acc == "0" && c != decimal -> {
                    c.toString()
                }
                acc.contains(decimal) && acc.length - acc.indexOf(decimal) > 8 -> {
                    acc
                }
                else -> {
                    "$acc$c"
                }
            }
        }
        twig("initializing view models stream")
        uiModels = DependenciesHolder.synchronizer.run {
            combine(
                status,
                processorInfo,
                orchardBalances,
                saplingBalances,
                transparentBalances,
                zec,
                pendingTransactions.distinctUntilChanged()
                // unfortunately we have to use an untyped array here rather than typed parameters because combine only supports up to 5 typed params
            ) { flows ->
                val unminedCount = (flows[6] as List<PendingTransaction>).count {
                    it.isSubmitSuccess() && !it.isMined()
                }
                UiModel(
                    status = flows[0] as Synchronizer.Status,
                    processorInfo = flows[1] as CompactBlockProcessor.ProcessorInfo,
                    orchardBalance = flows[2] as WalletBalance?,
                    saplingBalance = flows[3] as WalletBalance?,
                    transparentBalance = flows[4] as WalletBalance?,
                    pendingSend = flows[5] as String,
                    unminedCount = unminedCount
                )
            }.onStart { emit(UiModel(orchardBalance = null, saplingBalance = null, transparentBalance = null)) }
        }.conflate()
        initialized = true
    }

    override fun onCleared() {
        super.onCleared()
        twig("HomeViewModel cleared!")
    }

    suspend fun onChar(c: Char) {
        _typedChars.send(c)
    }

    data class UiModel(
        val status: Synchronizer.Status = DISCONNECTED,
        val processorInfo: CompactBlockProcessor.ProcessorInfo = CompactBlockProcessor.ProcessorInfo(null, null, null, null, null),
        val orchardBalance: WalletBalance?,
        val saplingBalance: WalletBalance?,
        val transparentBalance: WalletBalance?,
        val pendingSend: String = "0",
        val unminedCount: Int = 0
    ) {
        // Note: the wallet is effectively empty if it cannot cover the miner's fee
        val hasFunds: Boolean get() = (saplingBalance?.available?.value ?: 0) > (MINERS_FEE.value.toDouble() / Zatoshi.ZATOSHI_PER_ZEC) // 0.00001
        val hasSaplingBalance: Boolean get() = (saplingBalance?.total?.value ?: 0) > 0L
        val hasAutoshieldFunds: Boolean get() = (transparentBalance?.available?.value ?: 0) >= ZcashWalletApp.instance.autoshieldThreshold
        val isSynced: Boolean get() = status == SYNCED
        val isSendEnabled: Boolean get() = isSynced && hasFunds
        // Consider the wallet synced when within 100 blocks of the tip
        // so routine polling of 1-2 new blocks doesn't flash "Scanning..." text
        val isEffectivelySynced: Boolean get() {
            val scanned = processorInfo.lastScannedHeight?.value ?: return false
            val tip = processorInfo.networkBlockHeight?.value ?: return false
            return tip > 0 && (tip - scanned) < 100
        }

        // Processor Info
        val isDownloading = status == DOWNLOADING
        val isScanning = status == SCANNING
        val isValidating = status == VALIDATING
        val isDisconnected = status == DISCONNECTED
        val downloadProgress: Int get() {
            return processorInfo.run {
                if (lastDownloadRange?.isEmpty() == true) {
                    100
                } else {
                    val progress =
                        ((((lastDownloadedHeight?.value ?: 0) - (lastDownloadRange?.start?.value ?: 0) + 1).coerceAtLeast(0).toFloat() / ((lastDownloadRange?.endInclusive?.value ?: 0) - (lastDownloadRange?.start?.value ?: 0) + 1)) * 100.0f).coerceAtMost(
                            100.0f
                        ).roundToInt()
                    progress
                }
            }
        }
        val scanProgress: Int get() {
            return processorInfo.run {
                if (lastScanRange?.isEmpty() == true) {
                    100
                } else {
                    val progress = ((((lastScannedHeight?.value ?: 0) - (lastScanRange?.start?.value ?: 0) + 1).coerceAtLeast(0).toFloat() / ((lastScanRange?.endInclusive?.value ?: 0) - (lastScanRange?.start?.value ?: 0) + 1)) * 100.0f).coerceAtMost(100.0f).roundToInt()
                    progress
                }
            }
        }
        val overallProgress: Int get() {
            return processorInfo.run {
                val scanned = lastScannedHeight?.value ?: return@run 0
                val tip = networkBlockHeight?.value ?: return@run 0
                if (tip <= 0) 0
                else ((scanned.toFloat() / tip.toFloat()) * 100.0f).coerceIn(0f, 100f).roundToInt()
            }
        }
        val lastScannedBlockHeight: Long get() = processorInfo.lastScannedHeight?.value ?: 0
        val lastDownloadedBlockHeight: Long get() = processorInfo.lastDownloadedHeight?.value ?: 0
        val networkHeight: Long get() = processorInfo.networkBlockHeight?.value ?: 0
        val totalProgress: Float get() {
            val downloadWeighted = 0.40f * (downloadProgress.toFloat() / 100.0f).coerceAtMost(1.0f)
            val scanWeighted = 0.60f * (scanProgress.toFloat() / 100.0f).coerceAtMost(1.0f)
            return downloadWeighted.coerceAtLeast(0.0f) + scanWeighted.coerceAtLeast(0.0f)
        }
    }
}
