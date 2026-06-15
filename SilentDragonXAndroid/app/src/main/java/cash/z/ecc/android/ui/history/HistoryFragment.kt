package cash.z.ecc.android.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import cash.z.ecc.android.R
import cash.z.ecc.android.databinding.FragmentHistoryBinding
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.db.entity.ConfirmedTransaction
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.HistoryScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Transaction history, rendered with Compose ([HistoryScreen]). Observes the cleared transactions
 * flow; rows open a Compose detail screen. A "syncing" empty-state avoids records looking like they
 * vanished while the wallet is still scanning/rebuilding.
 */
class HistoryFragment : BaseFragment<FragmentHistoryBinding>() {
    override val screen = Report.Screen.HISTORY

    private val viewModel: HistoryViewModel by activityViewModels()
    private val txState = MutableStateFlow<List<ConfirmedTransaction>>(emptyList())
    private val syncedState = MutableStateFlow(false)
    private var txJob: Job? = null

    // Only to satisfy BaseFragment's abstract contract; never inflated (we render Compose instead).
    override fun inflate(inflater: LayoutInflater): FragmentHistoryBinding =
        FragmentHistoryBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val txs by txState.collectAsState()
            val synced by syncedState.collectAsState()
            DragonXTheme {
                HistoryScreen(
                    transactions = txs,
                    synced = synced,
                    onBack = { mainActivity?.navController?.popBackStack() },
                    onTxClick = { tx ->
                        viewModel.selectedTransaction.value = tx
                        mainActivity?.safeNavigate(R.id.action_nav_history_to_nav_transaction)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Only subscribe to the transaction PagedList once (effectively) synced. Subscribing while the
        // scanner is actively writing every batch ignites a Paging DataSource invalidation storm (the
        // ComputableFlow recomputes thousands of times -> "adding an invalidated callback" x1000s),
        // which ANRs and gets the app killed mid-scan, losing sync progress.
        //
        // Gate on the SAME 100-block hysteresis the home/balance card uses (HomeViewModel
        // .isEffectivelySynced) rather than the raw status: the raw status dips out of SYNCED on EVERY
        // new block, which made the whole history screen flash the "同步中" placeholder<->list every
        // block. Within 100 blocks of the tip the scanner is only doing trivial catch-ups (no storm),
        // so the list is stable and safe to show. Only a genuine heavy resync (>100 behind) tears the
        // subscription down — and we keep the last list rather than blanking it.
        combine(
            DependenciesHolder.synchronizer.status,
            DependenciesHolder.synchronizer.processorInfo
        ) { status, info ->
            if (status == Synchronizer.Status.SYNCED) {
                true
            } else {
                val scanned = info.lastScannedHeight?.value
                val tip = info.networkBlockHeight?.value
                scanned != null && tip != null && tip > 0L && (tip - scanned) < 100L
            }
        }
            .distinctUntilChanged()
            .onEach { synced ->
                syncedState.value = synced
                if (synced) {
                    if (txJob == null) {
                        // filterNotNull guards against PagedList placeholder nulls; the empty-guard +
                        // distinctUntilChanged stop the list blanking-and-refilling (flicker) when a
                        // periodic sync refresh re-queries the PagedList.
                        txJob = viewModel.transactions
                            .map { it.filterNotNull() }
                            .distinctUntilChanged()
                            .onEach { list ->
                                if (list.isNotEmpty() || txState.value.isEmpty()) {
                                    txState.value = list
                                }
                            }
                            .launchIn(resumedScope)
                    }
                } else {
                    // Real resync: stop consuming the PagedList (storm-safe) but keep the last list;
                    // the "同步中" placeholder shows via syncedState=false anyway.
                    txJob?.cancel()
                    txJob = null
                }
            }
            .launchIn(resumedScope)
    }

    override fun onPause() {
        super.onPause()
        txJob?.cancel()
        txJob = null
    }
}
