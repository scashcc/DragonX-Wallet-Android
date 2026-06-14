package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.z.ecc.android.ext.WalletZecFormmatter
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.ui.home.HomeViewModel

/**
 * Modern home screen. Replaces the old number-pad-first home with a balance card, a synchronizer
 * status line, primary actions (发送/收款/合并零钱/历史) and a recent-transactions entry. It renders
 * the same [HomeViewModel.UiModel] the proven engine already produces; navigation is delegated back
 * to the host Fragment via the callbacks.
 */
@Composable
fun HomeScreen(
    state: HomeViewModel.UiModel?,
    nodeHost: String,
    walletLabel: String,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onConsolidate: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            TopBar(state, walletLabel, onSettings)
            Spacer(Modifier.height(20.dp))
            BalanceCard(state, nodeHost)
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionTile(Icons.Filled.Send, "发送", DragonXGreen, Modifier.weight(1f), onSend)
                ActionTile(Icons.Filled.KeyboardArrowDown, "收款", DragonXBlue, Modifier.weight(1f), onReceive)
                ActionTile(Icons.Filled.Refresh, "合并零钱", DragonXPurple, Modifier.weight(1f), onConsolidate)
                ActionTile(Icons.Filled.List, "历史", TextPrimary, Modifier.weight(1f), onHistory)
            }
            Spacer(Modifier.height(26.dp))
            SectionHeader("最近交易 Recent", "查看全部", onHistory)
            Spacer(Modifier.height(12.dp))
            RecentEntryCard(onHistory)
        }
    }
}

@Composable
private fun TopBar(
    state: HomeViewModel.UiModel?,
    walletLabel: String,
    onSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("DragonX 钱包", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(walletLabel, color = TextSecondary, fontSize = 13.sp)
        }
        val height = state?.networkHeight ?: 0L
        if (height > 0L) {
            Pill(text = "区块 #$height", dotColor = null)
            Spacer(Modifier.width(10.dp))
        }
        Surface(
            onClick = onSettings,
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard2,
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun BalanceCard(
    state: HomeViewModel.UiModel?,
    nodeHost: String,
) {
    val status = state?.status ?: Synchronizer.Status.DISCONNECTED
    val synced = state?.isSynced == true || state?.isEffectivelySynced == true
    val (statusLabel, statusColor) = statusOf(status, synced)

    Surface(shape = RoundedCornerShape(24.dp), color = SurfaceCard) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Pill(text = nodeHost, dotColor = if (status == Synchronizer.Status.DISCONNECTED) NegativeRed else PositiveGreen)
                Pill(text = statusLabel, dotColor = statusColor)
            }
            Spacer(Modifier.height(18.dp))

            val available = state?.saplingBalance?.available
            val balanceText = if (available != null) WalletZecFormmatter.toZecStringFull(available) else "—"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(balanceText, color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("DRGX", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(4.dp))

            val total = state?.saplingBalance?.total
            val pendingText = when {
                available != null && total != null && total.value > available.value ->
                    "可用余额 · 待确认 +${WalletZecFormmatter.toZecStringFull(total.minus(available))}"
                available != null -> "可用余额 Available"
                else -> "余额更新中…"
            }
            Text(pendingText, color = TextSecondary, fontSize = 13.sp)

            if (!synced && status != Synchronizer.Status.DISCONNECTED && state != null) {
                Spacer(Modifier.height(16.dp))
                val progress = (state.overallProgress.coerceIn(0, 100)) / 100f
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = DragonXGreen,
                    trackColor = SurfaceCard2,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$statusLabel ${state.overallProgress}% · 同步期间请保持前台、勿锁屏",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun RecentEntryCard(onHistory: () -> Unit) {
    Surface(
        onClick = onHistory,
        shape = RoundedCornerShape(18.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("查看交易记录", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("收款与转账明细 Tap to view history", color = TextDim, fontSize = 12.sp)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

private fun statusOf(status: Synchronizer.Status, synced: Boolean): Pair<String, androidx.compose.ui.graphics.Color> {
    if (synced) return "已同步" to PositiveGreen
    return when (status) {
        Synchronizer.Status.SYNCED -> "已同步" to PositiveGreen
        Synchronizer.Status.DOWNLOADING -> "下载中" to WarnAmber
        Synchronizer.Status.VALIDATING -> "校验中" to WarnAmber
        Synchronizer.Status.SCANNING -> "扫描中" to WarnAmber
        Synchronizer.Status.ENHANCING -> "补充详情" to WarnAmber
        Synchronizer.Status.PREPARING -> "准备中" to WarnAmber
        Synchronizer.Status.STOPPED -> "已停止" to NegativeRed
        Synchronizer.Status.DISCONNECTED -> "连接中" to WarnAmber
    }
}
