package cash.z.ecc.android.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.databinding.FragmentHomeBinding
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.ext.WalletManager
import cash.z.ecc.android.ext.showSharedLibraryCriticalError
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.sdk.db.entity.ConfirmedTransaction
import cash.z.ecc.android.sdk.ext.onFirstWith
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.HomeScreen
import cash.z.ecc.android.ui.setup.WalletSetupViewModel
import cash.z.ecc.android.ui.setup.WalletSetupViewModel.WalletSetupState.NO_SEED
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Home screen. The old number-pad XML UI has been replaced by a modern Jetpack Compose screen
 * ([HomeScreen]) rendered via a ComposeView. The critical boot logic (seed check -> create/open
 * wallet -> startSync) and the synchronizer flow wiring are unchanged; only the rendering moved to
 * Compose. The proven HomeViewModel still produces the UiModel; we push each emission into a
 * StateFlow the Compose screen observes.
 */
@kotlinx.coroutines.ObsoleteCoroutinesApi
class HomeFragment : BaseFragment<FragmentHomeBinding>() {
    override val screen = Report.Screen.HOME

    private val walletSetup: WalletSetupViewModel by activityViewModels()
    private val viewModel: HomeViewModel by viewModels()

    private val recentTxState = MutableStateFlow<List<ConfirmedTransaction>>(emptyList())

    // Only here to satisfy BaseFragment's abstract contract; never inflated (we use Compose instead).
    override fun inflate(inflater: LayoutInflater): FragmentHomeBinding =
        FragmentHomeBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val ui by viewModel.homeState.collectAsState()
            val recent by recentTxState.collectAsState()
            val nodeHost = remember { currentNodeHost() }
            val walletLabel = remember { currentWalletLabel() }
            DragonXTheme {
                HomeScreen(
                    state = ui,
                    nodeHost = nodeHost,
                    walletLabel = walletLabel,
                    recentTx = recent,
                    onSend = { mainActivity?.safeNavigate(R.id.action_nav_home_to_send) },
                    onReceive = { mainActivity?.safeNavigate(R.id.action_nav_home_to_nav_receive) },
                    onConsolidate = { mainActivity?.safeNavigate(R.id.action_nav_home_to_nav_consolidate) },
                    onHistory = { mainActivity?.safeNavigate(R.id.action_nav_home_to_nav_history) },
                    onSettings = { mainActivity?.safeNavigate(R.id.action_nav_home_to_nav_profile) },
                )
            }
        }
    }

    private fun currentNodeHost(): String =
        DependenciesHolder.prefs[Const.Pref.SERVER_HOST] ?: Const.Default.Server.HOST

    private fun currentWalletLabel(): String =
        WalletManager.list().firstOrNull { it.isActive }?.label ?: "钱包 1 Wallet 1"

    //
    // Lifecycle (boot logic preserved verbatim from the original View-based home)
    //

    override fun onAttach(context: Context) {
        twig("HomeFragment.onAttach")
        super.onAttach(context)

        walletSetup.checkSeed().onFirstWith(lifecycleScope) {
            if (it == NO_SEED) {
                // No wallet yet -> create/restore flow (leads to startSync later, after keys exist).
                twig("Previous wallet not found, therefore, launching seed creation flow")
                mainActivity?.setLoading(false)
                mainActivity?.safeNavigate(R.id.action_nav_home_to_create_wallet)
            } else {
                twig("Previous wallet found. Re-opening it.")
                // No full-screen "Loading…" overlay: the Compose home renders immediately and shows
                // its own sync status (连接中 / 扫描中 X%) instead of a bare blocking spinner.
                try {
                    walletSetup.openStoredWallet()
                    mainActivity?.startSync()
                } catch (e: UnsatisfiedLinkError) {
                    mainActivity?.showSharedLibraryCriticalError(e)
                }
                twig("Done reopening wallet.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launchWhenSyncReady(::onSyncReady)
    }

    private fun onSyncReady() {
        twig("Sync ready! Monitoring synchronizer state...")
        monitorUiModelChanges()

        // When the synchronizer is restarted (e.g. after a server switch), rebind to the new instance.
        mainActivity?.mainViewModel?.syncRestarted
            ?.filter { it }
            ?.onEach {
                twig("Sync restarted detected — reinitializing HomeViewModel flows")
                mainActivity?.mainViewModel?.syncRestarted?.value = false
                viewModel.reinitialize()
                monitorUiModelChanges()
            }
            ?.launchIn(resumedScope)
    }

    private fun monitorUiModelChanges() {
        viewModel.initializeMaybe()
        viewModel.uiModels
            .onEach { viewModel.homeState.value = it }
            .launchIn(resumedScope)
        DependenciesHolder.synchronizer.clearedTransactions
            .onEach { recentTxState.value = it.filterNotNull() }
            .launchIn(resumedScope)
    }
}
