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
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.WalletManager
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.PrivateKeyRestoreScreen
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Restore a wallet from a Sapling spending key (secret-extended-key-main…). Delegates to
 * [WalletSetupViewModel.importWalletFromSpendingKey], which validates the key, stores it and sets up
 * the synchronizer. The 24-word grid restore is a separate, untouched screen.
 */
class PrivateKeyRestoreFragment : Fragment() {

    private val walletSetup: WalletSetupViewModel by activityViewModels()
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val latestHeight = MutableStateFlow(0L)

    // When launched from the wallet manager, restore into a NEW wallet slot instead of importing into
    // the single active wallet. Defaults to false (first-launch onboarding flow).
    private val createNewWallet: Boolean by lazy { arguments?.getBoolean("createNewWallet", false) ?: false }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val isBusy by busy.collectAsState()
            val err by error.collectAsState()
            val h by latestHeight.collectAsState()
            DragonXTheme {
                PrivateKeyRestoreScreen(
                    busy = isBusy,
                    error = err,
                    latestHeight = h,
                    onBack = { (activity as? MainActivity)?.navController?.popBackStack() },
                    onRestore = { key, birthday -> onRestore(key, birthday) },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            latestHeight.value = runCatching {
                BlockHeight.ofLatestCheckpoint(
                    ZcashWalletApp.instance,
                    ZcashWalletApp.instance.defaultNetwork
                ).value
            }.getOrDefault(0L)
        }
    }

    private fun onRestore(key: String, birthdayStr: String) = viewLifecycleOwner.lifecycleScope.launch {
        busy.value = true
        error.value = null
        try {
            val network = ZcashWalletApp.instance.defaultNetwork
            val birthday = birthdayStr.toLongOrNull()?.let { BlockHeight.new(network, it) }
            if (createNewWallet) {
                // Add as a new wallet slot, then restart for a clean reload (same as create/switch).
                val resolvedBirthday = birthday
                    ?: BlockHeight.ofLatestCheckpoint(ZcashWalletApp.instance, network)
                withContext(Dispatchers.IO) {
                    runCatching { DependenciesHolder.synchronizer.stop() }
                    WalletManager.restoreFromSpendingKey(
                        label = "钱包 ${WalletManager.count() + 1}",
                        spendingKey = key,
                        birthday = resolvedBirthday,
                    )
                }
                (activity as? MainActivity)?.restartApp()
            } else {
                walletSetup.importWalletFromSpendingKey(key, birthday)
                (activity as? MainActivity)?.startSync()
                (activity as? MainActivity)?.safeNavigate(R.id.action_nav_pk_restore_to_nav_home)
            }
        } catch (t: Throwable) {
            twig("Private-key restore failed: $t")
            busy.value = false
            error.value = "恢复失败：私钥无效或格式不正确。\n${t.message ?: ""}"
        }
    }
}
