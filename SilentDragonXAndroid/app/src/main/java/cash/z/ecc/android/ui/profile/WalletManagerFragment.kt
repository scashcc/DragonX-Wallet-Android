package cash.z.ecc.android.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.WalletManager
import cash.z.ecc.android.ext.showCriticalMessage
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.WalletItem
import cash.z.ecc.android.ui.compose.WalletManagerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Multi-wallet manager (Compose). Switching/creating does heavy work (key derivation, stopping the
 * synchronizer) OFF the main thread behind a busy spinner, then restarts the app for a clean reload.
 * Doing it on the main thread while a heavy scan is running froze the UI ("no response").
 */
class WalletManagerFragment : Fragment() {

    private val busy = MutableStateFlow(false)
    private val busyText = MutableStateFlow("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        WalletManager.migrateIfNeeded()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isBusy by busy.collectAsState()
                val txt by busyText.collectAsState()
                DragonXTheme {
                    WalletManagerScreen(
                        wallets = WalletManager.list().map { WalletItem(it.index, it.label, it.isActive) },
                        busy = isBusy,
                        busyText = txt,
                        onSwitch = { i -> doSwitch(i) },
                        onCreate = { label -> createWallet(label) },
                        // Reuse the existing restore screens, but in "create a new wallet slot" mode
                        // (createNewWallet=true) so they add a wallet instead of importing into the
                        // single active wallet.
                        onRestoreSeed = {
                            (activity as? MainActivity)?.navController?.navigate(
                                R.id.nav_restore, bundleOf("createNewWallet" to true)
                            )
                        },
                        onRestorePrivateKey = {
                            (activity as? MainActivity)?.navController?.navigate(
                                R.id.nav_pk_restore, bundleOf("createNewWallet" to true)
                            )
                        },
                        onBack = { (activity as? MainActivity)?.navController?.popBackStack() },
                    )
                }
            }
        }
    }

    private fun doSwitch(i: Int) {
        busyText.value = "正在切换钱包…"
        busy.value = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    runCatching { DependenciesHolder.synchronizer.stop() }
                    WalletManager.switchTo(i)
                }
                (activity as? MainActivity)?.restartApp()
            } catch (t: Throwable) {
                busy.value = false
                (activity as? MainActivity)?.showCriticalMessage("切换钱包失败 Switch failed", t.message ?: t.toString())
            }
        }
    }

    private fun createWallet(label: String) {
        busyText.value = "正在创建新钱包…（同步繁忙时可能要等十几秒）"
        busy.value = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Free the (possibly busy) Rust backend first so key derivation isn't blocked.
                    runCatching { DependenciesHolder.synchronizer.stop() }
                    WalletManager.createWallet(label)
                }
                (activity as? MainActivity)?.restartApp()
            } catch (t: Throwable) {
                busy.value = false
                (activity as? MainActivity)?.showCriticalMessage("新建钱包失败 Create failed", t.message ?: t.toString())
            }
        }
    }
}
