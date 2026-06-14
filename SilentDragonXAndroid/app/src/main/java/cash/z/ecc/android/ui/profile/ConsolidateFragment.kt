package cash.z.ecc.android.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.databinding.FragmentConsolidateBinding
import cash.z.ecc.android.sdk.Synchronizer.Status.SYNCED
import cash.z.ecc.android.sdk.db.entity.PendingTransaction
import cash.z.ecc.android.sdk.db.entity.isMined
import cash.z.ecc.android.sdk.db.entity.isSubmitSuccess
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.ConsolidateScreen
import cash.z.ecc.android.ui.compose.ConsolidateUiState
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "合并零钱" (consolidate small notes), Compose UI. Runs the fully-automatic sweep: repeatedly
 * merges the smallest notes back to the wallet's own address, a batch at a time, until nothing is
 * left worth consolidating. Progress is reported by *confirmed* batches (real on-chain
 * confirmations), never just "submitted to mempool". The engine ([Synchronizer.consolidate]) is
 * unchanged.
 */
class ConsolidateFragment : BaseFragment<FragmentConsolidateBinding>() {

    private val viewModel: ProfileViewModel by viewModels()
    private val uiState = MutableStateFlow<ConsolidateUiState>(ConsolidateUiState.Idle)
    private var running = false

    // Only to satisfy BaseFragment's abstract contract; never inflated (we render Compose instead).
    override fun inflate(inflater: LayoutInflater): FragmentConsolidateBinding =
        FragmentConsolidateBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val state by uiState.collectAsState()
            DragonXTheme {
                ConsolidateScreen(
                    state = state,
                    onBack = { if (!running) mainActivity?.navController?.popBackStack() },
                    onStart = { startConsolidation() },
                    onRescan = {
                        viewModel.wipe()
                        mainActivity?.restartApp()
                    },
                )
            }
        }
    }

    private fun startConsolidation() = viewLifecycleOwner.lifecycleScope.launch {
        if (running) return@launch
        // The wallet must be fully synced so the notes have valid witnesses at the anchor height.
        val synced = try {
            viewModel.synchronizer.status.first() == SYNCED
        } catch (t: Throwable) {
            false
        }
        if (!synced) {
            mainActivity?.showSnackbar("请先完成同步，再合并零钱")
            return@launch
        }

        running = true
        // Each batch emits when submitted and again when it confirms (or fails/expires); we key by
        // tx id and recompute tallies on every update. Progress counts *confirmed* batches.
        val seen = HashMap<Long, PendingTransaction>()
        uiState.value = ConsolidateUiState.Running(0, 0)
        try {
            viewModel.consolidate().collect { tx ->
                seen[tx.id] = tx
                val confirmed = seen.values.count { it.isMined() }
                val submitted = seen.values.count { it.isMined() || it.isSubmitSuccess() }
                uiState.value = ConsolidateUiState.Running(confirmed, submitted)
            }
            val confirmed = seen.values.count { it.isMined() }
            val submitted = seen.values.count { it.isMined() || it.isSubmitSuccess() }
            uiState.value =
                if (submitted == 0) ConsolidateUiState.Nothing
                else ConsolidateUiState.Done(confirmed, submitted)
        } catch (t: Throwable) {
            uiState.value = if (isNeedsRescan(t)) {
                twig("consolidation needs a full rescan: $t")
                ConsolidateUiState.NeedsRescan
            } else {
                twig("consolidation failed: $t")
                ConsolidateUiState.Error(t.message ?: t.toString())
            }
        } finally {
            running = false
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
