package cash.z.ecc.android.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cash.z.ecc.android.BuildConfig
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.databinding.FragmentProfileBinding
import cash.z.ecc.android.ext.*
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.ext.toAbbreviatedAddress
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.ProfileScreen
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * "我的" settings hub, rendered with Compose ([ProfileScreen]). The leaf actions reuse the proven
 * flows: backup/export-keys are biometric-gated and navigate to their existing screens; the node
 * picker, multi-wallet switcher and rescan reuse the existing (already latency-aware) dialogs.
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {
    override val screen = Report.Screen.PROFILE

    private val viewModel: ProfileViewModel by viewModels()
    private val addressState = MutableStateFlow("…")
    private val labelState = MutableStateFlow("钱包 1 Wallet 1")

    // Only to satisfy BaseFragment's abstract contract; never inflated (we render Compose instead).
    override fun inflate(inflater: LayoutInflater): FragmentProfileBinding =
        FragmentProfileBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val addr by addressState.collectAsState()
            val label by labelState.collectAsState()
            DragonXTheme {
                ProfileScreen(
                    walletLabel = label,
                    address = addr,
                    version = BuildConfig.VERSION_NAME,
                    onBack = { mainActivity?.navController?.popBackStack() },
                    onSwitchWallet = { onWalletManager() },
                    onBackup = { gatedNavigate(R.id.action_nav_profile_to_nav_backup) },
                    onExportKeys = { gatedNavigate(R.id.action_nav_profile_to_nav_export_keys) },
                    onChooseNode = { mainActivity?.showServerPickerDialog(userInitiated = true) },
                    onConsolidate = { mainActivity?.safeNavigate(R.id.action_nav_profile_to_nav_consolidate) },
                    onRescan = { onRescanWallet() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        labelState.value = WalletManager.list().firstOrNull { it.isActive }?.label ?: "钱包 1 Wallet 1"
        resumedScope.launch {
            addressState.value = try {
                viewModel.getShieldedAddress().toAbbreviatedAddress(12, 12)
            } catch (t: Throwable) {
                "—"
            }
        }
    }

    /** Biometric/device-credential gate, then navigate (used for backup & export keys). */
    private fun gatedNavigate(navId: Int) {
        mainActivity?.let { main ->
            main.authenticate(
                getString(R.string.biometric_backup_phrase_description),
                getString(R.string.biometric_backup_phrase_title)
            ) {
                main.safeNavigate(navId)
            }
        }
    }

    //
    // Multi-wallet (unchanged dialog logic)
    //

    private fun onWalletManager() {
        WalletManager.migrateIfNeeded()
        val wallets = WalletManager.list()
        val items = (
            wallets.map { (if (it.isActive) "● " else "") + it.label } +
                getString(R.string.wallet_new)
            ).toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.profile_wallets))
            .setItems(items) { _, which ->
                if (which < wallets.size) {
                    val w = wallets[which]
                    if (!w.isActive) {
                        WalletManager.switchTo(w.index)
                        mainActivity?.restartApp()
                    }
                } else {
                    promptNewWallet()
                }
            }
            .setNegativeButton("关闭 Close", null)
            .show()
    }

    private fun promptNewWallet() {
        val input = android.widget.EditText(requireContext()).apply {
            setText("钱包 ${WalletManager.count() + 1}")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.wallet_new))
            .setMessage(getString(R.string.wallet_new_warning))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val label = input.text.toString().ifBlank { "钱包 ${WalletManager.count() + 1}" }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        WalletManager.createWallet(label)
                        mainActivity?.showConfirmation(
                            "✅ 新钱包已创建",
                            "已切换到「$label」。\n\n⚠️ 请在重启后立即到「我的 → 备份助记词」备份这个新钱包自己的 24 个助记词，否则一旦丢失将无法找回。",
                            "好，重启加载"
                        ) { mainActivity?.restartApp() }
                    } catch (t: Throwable) {
                        mainActivity?.showCriticalMessage(
                            "新建钱包失败 Create failed",
                            t.message ?: t.toString()
                        )
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    //
    // Rescan (unchanged dialog logic)
    //

    private fun onRescanWallet() {
        val quickDistance = viewModel.quickScanDistance()
        val fullDistance = viewModel.fullScanDistance()
        mainActivity?.showRescanWalletDialog(
            String.format("%,d", quickDistance),
            viewModel.blocksToMinutesString(quickDistance),
            String.format("%,d", fullDistance),
            viewModel.blocksToMinutesString(fullDistance),
            onFullRescan = ::onFullRescan,
            onQuickRescan = ::onQuickRescan,
            onWipe = ::onWipe
        )
    }

    private fun onFullRescan() {
        (viewModel.synchronizer as SdkSynchronizer).coroutineScope.launch {
            try {
                viewModel.fullRescan()
                Toast.makeText(ZcashWalletApp.instance, "Performing full rescan!", Toast.LENGTH_LONG).show()
                mainActivity?.navController?.popBackStack()
            } catch (t: Throwable) {
                mainActivity?.showCriticalMessage(
                    "Full Rescan Failed",
                    "Unable to perform full rescan due to error:\n\n${t.message}"
                )
            }
        }
    }

    private fun onQuickRescan() {
        viewModel.viewModelScope.launch {
            try {
                viewModel.quickRescan()
                Toast.makeText(ZcashWalletApp.instance, "Performing quick rescan!", Toast.LENGTH_LONG).show()
                mainActivity?.navController?.popBackStack()
            } catch (t: Throwable) {
                mainActivity?.showCriticalMessage("Quick Rescan Failed", "Unable to perform quick rescan due to error:\n\n${t.message}")
            }
        }
    }

    private fun onWipe() {
        mainActivity?.showConfirmation(
            "清除区块数据并重新同步？",
            "将删除本地区块数据并从头同步（用于修复同步卡住或区块库损坏）。\n\n" +
                "你的助记词和地址不受影响，无需重装，也不用重新输入助记词。\n\n" +
                "应用会自动重启并开始重新同步。是否继续？",
            "清除并重新同步"
        ) {
            viewModel.wipe()
            mainActivity?.restartApp()
        }
    }
}
