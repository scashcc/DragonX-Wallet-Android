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
                onRescanError(t, "完整重扫失败")
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
                onRescanError(t, "快速重扫失败")
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

    /**
     * Rewind (quick/full rescan) reads & writes the local block DB, so it CANNOT fix a corrupt
     * ("malformed") DB — the rewind itself throws. When that happens we auto-escalate to a clean
     * wipe+resync (the only thing that repairs corruption). Seed/funds are never touched.
     */
    private fun onRescanError(t: Throwable, label: String) {
        val act = activity as? MainActivity ?: return
        act.runOnUiThread {
            if (isLocalDataCorrupt(t)) {
                Toast.makeText(
                    ZcashWalletApp.instance,
                    "本地区块数据已损坏，无法快速回退。已自动改为「清除并重新同步」（从头重建，唯一能修复损坏的方式）。" +
                        "助记词/资金不受影响，请耐心等待同步…",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.wipe()
                act.restartApp()
            } else {
                act.showCriticalMessage(label, t.message ?: t.toString())
            }
        }
    }

    /**
     * Walk the whole cause chain (and check the exception type), because the SQLite corruption error
     * thrown from the Rust/JNI rewind is usually *wrapped* — the "malformed" text lives in a nested
     * cause, not in the top-level message. The old check only looked at t.message, so a wrapped
     * corruption slipped through to a scary critical-error dialog instead of auto-rebuilding.
     */
    private fun isLocalDataCorrupt(error: Throwable): Boolean {
        var c: Throwable? = error
        var depth = 0
        while (c != null && depth < 15) {
            if (c is android.database.sqlite.SQLiteDatabaseCorruptException) return true
            val m = (c.message ?: "").lowercase(Locale.US)
            if (m.contains("malformed") ||
                m.contains("corrupt") ||
                m.contains("disk image") ||
                m.contains("not a database") ||
                m.contains("sqlite_corrupt") ||
                m.contains("(code 11")
            ) {
                return true
            }
            c = c.cause
            depth++
        }
        return false
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
