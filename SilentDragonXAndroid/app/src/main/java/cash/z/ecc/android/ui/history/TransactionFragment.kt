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
import cash.z.ecc.android.databinding.FragmentTransactionBinding
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.TransactionDetailScreen

/**
 * Transaction detail, rendered with Compose ([TransactionDetailScreen]). Reads the tapped
 * transaction from the shared HistoryViewModel.
 */
class TransactionFragment : BaseFragment<FragmentTransactionBinding>() {
    override val screen = Report.Screen.TRANSACTION
    private val viewModel: HistoryViewModel by activityViewModels()

    // Only to satisfy BaseFragment's abstract contract; never inflated (we render Compose instead).
    override fun inflate(inflater: LayoutInflater): FragmentTransactionBinding =
        FragmentTransactionBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val tx by viewModel.selectedTransaction.collectAsState()
            DragonXTheme {
                TransactionDetailScreen(
                    tx = tx,
                    latestHeight = viewModel.latestHeight?.value,
                    onBack = { mainActivity?.navController?.popBackStack() },
                    onCopyTxid = { mainActivity?.copyText(it, "交易 ID") },
                )
            }
        }
    }
}
