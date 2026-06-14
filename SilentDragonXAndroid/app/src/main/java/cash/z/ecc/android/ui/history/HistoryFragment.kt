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
import cash.z.ecc.android.databinding.FragmentHistoryBinding
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.sdk.db.entity.ConfirmedTransaction
import cash.z.ecc.android.sdk.ext.collectWith
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.HistoryScreen
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Transaction history, rendered with Compose ([HistoryScreen]). It observes the same cleared
 * transactions flow the proven engine exposes (HistoryViewModel.transactions).
 */
class HistoryFragment : BaseFragment<FragmentHistoryBinding>() {
    override val screen = Report.Screen.HISTORY

    private val viewModel: HistoryViewModel by activityViewModels()
    private val txState = MutableStateFlow<List<ConfirmedTransaction>>(emptyList())

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
            DragonXTheme {
                HistoryScreen(
                    transactions = txs,
                    onBack = { mainActivity?.navController?.popBackStack() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // filterNotNull guards against PagedList placeholder nulls.
        viewModel.transactions.collectWith(resumedScope) { txState.value = it.filterNotNull() }
    }
}
