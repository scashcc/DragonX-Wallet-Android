package cash.z.ecc.android.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.ext.WalletManager
import cash.z.ecc.android.ext.showCriticalMessage
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.WalletManagerScreen
import cash.z.ecc.android.ui.compose.WalletItem
import kotlinx.coroutines.launch

/**
 * Multi-wallet manager (Compose). Lists wallets, switches between them, or creates a new one.
 * Reuses the proven [WalletManager] (per-wallet key namespaces + DB alias); switching/creating
 * restarts the app for a guaranteed-clean reload.
 */
class WalletManagerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        WalletManager.migrateIfNeeded()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DragonXTheme {
                    WalletManagerScreen(
                        wallets = WalletManager.list().map { WalletItem(it.index, it.label, it.isActive) },
                        onSwitch = { i ->
                            WalletManager.switchTo(i)
                            (activity as? MainActivity)?.restartApp()
                        },
                        onCreate = { label -> createWallet(label) },
                        onBack = { (activity as? MainActivity)?.navController?.popBackStack() },
                    )
                }
            }
        }
    }

    private fun createWallet(label: String) = viewLifecycleOwner.lifecycleScope.launch {
        try {
            WalletManager.createWallet(label)
            (activity as? MainActivity)?.restartApp()
        } catch (t: Throwable) {
            (activity as? MainActivity)?.showCriticalMessage(
                "新建钱包失败 Create failed",
                t.message ?: t.toString()
            )
        }
    }
}
