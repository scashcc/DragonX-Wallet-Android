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
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.ext.showConfirmation
import cash.z.ecc.android.ext.showCriticalMessage
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.RescanScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Rescan options (Compose), reusing the proven ProfileViewModel rescan/wipe logic. */
class RescanFragment : androidx.fragment.app.Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private val quickInfo = MutableStateFlow("计算中…")
    private val fullInfo = MutableStateFlow("计算中…")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val q by quickInfo.collectAsState()
            val f by fullInfo.collectAsState()
            DragonXTheme {
                RescanScreen(
                    quickInfo = q,
                    fullInfo = f,
                    onQuick = { onQuickRescan() },
                    onFull = { onFullRescan() },
                    onWipe = { onWipe() },
                    onRescanFromHeight = { onRescanFromHeight(it) },
                    onBack = { (activity as? MainActivity)?.navController?.popBackStack() },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val q = runCatching { viewModel.quickScanDistance() }.getOrDefault(0)
                val f = runCatching { viewModel.fullScanDistance() }.getOrDefault(0L)
                quickInfo.value = "约 ${String.format(Locale.US, "%,d", q)} 块 · ${viewModel.blocksToMinutesString(q)}"
                fullInfo.value = "约 ${String.format(Locale.US, "%,d", f)} 块 · ${viewModel.blocksToMinutesString(f)}"
            }
        }
    }

    private fun onFullRescan() {
        (viewModel.synchronizer as SdkSynchronizer).coroutineScope.launch {
            try {
                viewModel.fullRescan()
                Toast.makeText(ZcashWalletApp.instance, "正在完整重扫…", Toast.LENGTH_LONG).show()
                (activity as? MainActivity)?.navController?.popBackStack()
            } catch (t: Throwable) {
                (activity as? MainActivity)?.showCriticalMessage("完整重扫失败", t.message ?: t.toString())
            }
        }
    }

    private fun onQuickRescan() {
        viewModel.viewModelScope.launch {
            try {
                viewModel.quickRescan()
                Toast.makeText(ZcashWalletApp.instance, "正在快速重扫…", Toast.LENGTH_LONG).show()
                (activity as? MainActivity)?.navController?.popBackStack()
            } catch (t: Throwable) {
                (activity as? MainActivity)?.showCriticalMessage("快速重扫失败", t.message ?: t.toString())
            }
        }
    }

    private fun onRescanFromHeight(height: Long) {
        val net = ZcashWalletApp.instance.defaultNetwork
        val h = height.coerceAtLeast(net.saplingActivationHeight.value)
        (activity as? MainActivity)?.showConfirmation(
            "从高度 ${String.format(Locale.US, "%,d", h)} 重扫？",
            "将把钱包生日改为该高度、清除本地区块数据并从那里重新同步。高度越低越慢；助记词/密钥不受影响。App 会自动重启。",
            "开始重扫"
        ) {
            DependenciesHolder.lockBox[Const.Backup.BIRTHDAY_HEIGHT] = h.toInt()
            viewModel.wipe()
            (activity as? MainActivity)?.restartApp()
        }
    }

    private fun onWipe() {
        (activity as? MainActivity)?.showConfirmation(
            "清除区块数据并重新同步？",
            "将删除本地区块数据并从头同步（修复同步卡住/区块库损坏）。\n\n" +
                "助记词/地址不受影响，无需重装，也不用重输助记词。App 会自动重启。是否继续？",
            "清除并重新同步"
        ) {
            viewModel.wipe()
            (activity as? MainActivity)?.restartApp()
        }
    }
}
