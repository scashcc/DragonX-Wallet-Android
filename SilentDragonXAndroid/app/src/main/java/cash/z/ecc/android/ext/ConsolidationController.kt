package cash.z.ecc.android.ext

import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.sdk.db.entity.PendingTransaction
import cash.z.ecc.android.sdk.db.entity.isMined
import cash.z.ecc.android.sdk.db.entity.isSubmitSuccess
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.ui.compose.ConsolidateUiState
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-lifetime owner of the "合并零钱" (consolidate small notes) sweep.
 *
 * Why this exists: the sweep is long-running (many rounds, each waiting for an on-chain
 * confirmation). Previously it was collected inside the ConsolidateFragment's `viewLifecycleScope`,
 * so navigating away / switching pages / rotating the screen cancelled it mid-flight and the user
 * had to start over. Here the job lives in a singleton scope that is NOT tied to any Fragment, so:
 *
 *  - **Switch pages / lock screen / background**: the sweep keeps running; returning to the page
 *    re-observes [state] and shows live progress (it never restarts from zero).
 *  - **Crash / process death**: every batch marks its notes spent in the data DB the instant it is
 *    submitted (see Synchronizer.consolidate), so already-submitted transactions are durable and can
 *    never be double-spent. We also persist an "in progress" flag; on next launch [wasInterrupted]
 *    reports it so the UI can offer to safely resume (re-running just picks up the notes that are
 *    still unspent — finished ones are skipped).
 *
 * The sweep is idempotent and safe to (re)start: [start] is a no-op while already [isRunning].
 */
object ConsolidationController {

    // Runs off the main thread; survives every Fragment/Activity. Never cancelled (process-lifetime),
    // so the only thing that stops an in-flight sweep is the process actually dying.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ConsolidateUiState>(ConsolidateUiState.Idle)
    val state: StateFlow<ConsolidateUiState> = _state

    @Volatile
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    private const val PREF_IN_PROGRESS = "dragonx_consolidation_in_progress"

    /** True if a sweep was running when the app was last killed (and isn't running now) -> resumable. */
    fun wasInterrupted(): Boolean =
        !isRunning && runCatching { DependenciesHolder.prefs.getBoolean(PREF_IN_PROGRESS) }.getOrDefault(false)

    private fun setInProgress(value: Boolean) {
        runCatching { DependenciesHolder.prefs.setBoolean(PREF_IN_PROGRESS, value) }
    }

    /**
     * Start (or resume) the sweep. No-op if one is already running. The caller is expected to have
     * verified the wallet is fully synced first.
     */
    fun start() {
        if (isRunning) return
        setInProgress(true)
        // Key each batch by tx id and recompute tallies on every update; progress counts *confirmed*
        // batches (real on-chain confirmations), not just mempool submissions.
        val seen = HashMap<Long, PendingTransaction>()
        _state.value = ConsolidateUiState.Running(0, 0)
        job = scope.launch {
            try {
                val sync = DependenciesHolder.synchronizer
                // Seed-derived for normal wallets, stored key for private-key-restored wallets.
                val sk = Keys.activeSpendingKey(sync.network)
                sync.consolidate(sk).collect { tx ->
                    seen[tx.id] = tx
                    val confirmed = seen.values.count { it.isMined() }
                    val submitted = seen.values.count { it.isMined() || it.isSubmitSuccess() }
                    _state.value = ConsolidateUiState.Running(confirmed, submitted)
                }
                val confirmed = seen.values.count { it.isMined() }
                val submitted = seen.values.count { it.isMined() || it.isSubmitSuccess() }
                _state.value =
                    if (submitted == 0) ConsolidateUiState.Nothing
                    else ConsolidateUiState.Done(confirmed, submitted)
                setInProgress(false)
            } catch (c: CancellationException) {
                // Only happens if the process scope itself is torn down; keep the in-progress flag so
                // the next launch knows it was interrupted and can offer to resume.
                throw c
            } catch (t: Throwable) {
                _state.value = if (isNeedsRescan(t)) {
                    twig("consolidation needs a full rescan: $t")
                    ConsolidateUiState.NeedsRescan
                } else {
                    twig("consolidation failed: $t")
                    ConsolidateUiState.Error(t.message ?: t.toString())
                }
                setInProgress(false)
            }
        }
    }

    /**
     * Mark the persisted "interrupted" flag as acknowledged without starting a sweep (e.g. the user
     * dismissed the resume hint). Does nothing if a sweep is currently running.
     */
    fun clearInterrupted() {
        if (!isRunning) setInProgress(false)
    }

    /** Reset the visible state back to Idle (e.g. after the user has seen a Done/Error result). */
    fun resetState() {
        if (!isRunning) _state.value = ConsolidateUiState.Idle
    }

    /**
     * If a previous sweep was interrupted by the app being killed and nothing is running now, surface
     * the resumable [ConsolidateUiState.Interrupted] hint. Safe to call every time the screen opens.
     */
    fun reflectInterruptedState() {
        if (wasInterrupted() && _state.value is ConsolidateUiState.Idle) {
            _state.value = ConsolidateUiState.Interrupted
        }
    }

    /** Detects the SDK's "funds exist but witnesses are missing -> needs a full rescan" signal. */
    private fun isNeedsRescan(t: Throwable): Boolean {
        var cause: Throwable? = t
        while (cause != null) {
            if (cause is TransactionEncoderException.ConsolidationNeedsRescanException) return true
            if (cause.message?.contains("DRAGONX_NEEDS_RESCAN") == true) return true
            cause = cause.cause
        }
        return false
    }
}
