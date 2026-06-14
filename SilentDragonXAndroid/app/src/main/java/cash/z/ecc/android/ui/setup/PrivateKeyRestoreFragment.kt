package cash.z.ecc.android.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.PrivateKeyRestoreScreen
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Restore a wallet from a Sapling spending key (secret-extended-key-main…). Delegates to
 * [WalletSetupViewModel.importWalletFromSpendingKey], which validates the key, stores it and sets up
 * the synchronizer. The 24-word grid restore is a separate, untouched screen.
 */
class PrivateKeyRestoreFragment : Fragment() {

    private val walletSetup: WalletSetupViewModel by activityViewModels()
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

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
                PrivateKeyRestoreScreen(
                    busy = isBusy,
                    error = err,
                    onBack = { (activity as? MainActivity)?.navController?.popBackStack() },
                    onRestore = { key, birthday -> onRestore(key, birthday) },
                )
            }
        }
    }

    private fun onRestore(key: String, birthdayStr: String) = viewLifecycleOwner.lifecycleScope.launch {
        busy.value = true
        error.value = null
        try {
            val network = ZcashWalletApp.instance.defaultNetwork
            val birthday = birthdayStr.toLongOrNull()?.let { BlockHeight.new(network, it) }
            walletSetup.importWalletFromSpendingKey(key, birthday)
            (activity as? MainActivity)?.startSync()
            (activity as? MainActivity)?.safeNavigate(R.id.action_nav_pk_restore_to_nav_home)
        } catch (t: Throwable) {
            twig("Private-key restore failed: $t")
            busy.value = false
            error.value = "恢复失败：私钥无效或格式不正确。\n${t.message ?: ""}"
        }
    }
}
