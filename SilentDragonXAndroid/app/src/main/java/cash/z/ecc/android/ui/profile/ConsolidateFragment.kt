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
import cash.z.ecc.android.ext.ConsolidationController
import cash.z.ecc.android.sdk.Synchronizer.Status.SYNCED
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.ConsolidateScreen
import cash.z.ecc.android.ui.compose.DragonXTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "合并零钱" (consolidate small notes), Compose UI. The actual sweep runs in the process-lifetime
 * [ConsolidationController], NOT this Fragment's scope, so it survives navigating away / switching
 * pages / locking the screen, and a crash mid-sweep can be safely resumed (already-submitted batches
 * are durable and never double-spent). This Fragment is just a thin view over the controller's state.
 */
class ConsolidateFragment : BaseFragment<FragmentConsolidateBinding>() {

    private val viewModel: ProfileViewModel by viewModels()

    // Only to satisfy BaseFragment's abstract contract; never inflated (we render Compose instead).
    override fun inflate(inflater: LayoutInflater): FragmentConsolidateBinding =
        FragmentConsolidateBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        // If a previous sweep was killed mid-run, show the resumable hint as soon as the screen opens.
        ConsolidationController.reflectInterruptedState()
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val state by ConsolidationController.state.collectAsState()
            DragonXTheme {
                ConsolidateScreen(
                    state = state,
                    // Leaving is safe now — the sweep keeps running in the background.
                    onBack = { mainActivity?.navController?.popBackStack() },
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
        if (ConsolidationController.isRunning) return@launch
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
        // Hand off to the process-lifetime controller; from here it survives this Fragment.
        ConsolidationController.start()
    }
}
