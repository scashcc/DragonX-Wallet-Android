package cash.z.ecc.android.ui.setup

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.databinding.FragmentLandingBinding
import cash.z.ecc.android.ext.showSharedLibraryCriticalError
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.LandingScreen
import cash.z.ecc.android.ui.setup.WalletSetupViewModel.WalletSetupState.SEED_WITHOUT_BACKUP
import cash.z.ecc.android.ui.setup.WalletSetupViewModel.WalletSetupState.SEED_WITH_BACKUP
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Onboarding landing screen, rendered with Compose ([LandingScreen]). Offers: create a new wallet,
 * restore from a 24-word seed (existing grid screen, untouched), or restore from a Sapling private
 * key. The create/restore engine calls (WalletSetupViewModel) are unchanged.
 */
class LandingFragment : BaseFragment<FragmentLandingBinding>() {
    override val screen = Report.Screen.LANDING

    private val walletSetup: WalletSetupViewModel by activityViewModels()
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    // Only to satisfy BaseFragment's abstract contract; never inflated (we render Compose instead).
    override fun inflate(inflater: LayoutInflater): FragmentLandingBinding =
        FragmentLandingBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val isBusy by busy.collectAsState()
            val err by error.collectAsState()
            DragonXTheme {
                LandingScreen(
                    busy = isBusy,
                    error = err,
                    onCreate = { onCreateWallet() },
                    onRestoreSeed = { mainActivity?.safeNavigate(R.id.action_nav_landing_to_nav_restore) },
                    onRestorePrivateKey = { mainActivity?.safeNavigate(R.id.action_nav_landing_to_nav_pk_restore) },
                )
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // If a wallet already exists but hasn't been backed up, jump straight to the backup screen.
        walletSetup.checkSeed().onEach {
            when (it) {
                SEED_WITHOUT_BACKUP, SEED_WITH_BACKUP -> mainActivity?.safeNavigate(R.id.nav_backup)
                else -> {}
            }
        }.launchIn(lifecycleScope)
    }

    private fun onCreateWallet() = viewLifecycleOwner.lifecycleScope.launch {
        busy.value = true
        error.value = null
        try {
            walletSetup.newWallet()
            mainActivity?.startSync()
            mainActivity?.safeNavigate(R.id.action_nav_landing_to_nav_backup)
        } catch (e: UnsatisfiedLinkError) {
            busy.value = false
            mainActivity?.showSharedLibraryCriticalError(e)
        } catch (t: Throwable) {
            twig("Failed to create wallet due to: $t")
            busy.value = false
            error.value = "创建钱包失败：${t.message ?: t.toString()}"
        }
    }
}
