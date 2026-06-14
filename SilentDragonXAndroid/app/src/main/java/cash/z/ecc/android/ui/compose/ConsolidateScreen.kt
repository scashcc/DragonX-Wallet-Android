package cash.z.ecc.android.ui.compose

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** UI state for the consolidation sweep, mirrored from PendingTransaction tallies by the Fragment. */
sealed interface ConsolidateUiState {
    object Idle : ConsolidateUiState
    data class Running(val confirmed: Int, val submitted: Int) : ConsolidateUiState
    data class Done(val confirmed: Int, val submitted: Int) : ConsolidateUiState
    object Nothing : ConsolidateUiState
    object NeedsRescan : ConsolidateUiState
    data class Error(val message: String) : ConsolidateUiState
}

@Composable
fun ConsolidateScreen(
    state: ConsolidateUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRescan: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val running = state is ConsolidateUiState.Running

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            ScreenHeader("合并零钱 Consolidate", onBack)
            Spacer(Modifier.height(20.dp))

            Surface(shape = RoundedCornerShape(20.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("把碎零钱并成大额", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "矿池/多笔小额到账会让钱包堆积大量碎零钱(note)，导致大额转账交易过大、矿工不打包。" +
                            "合并会把它们一笔笔并回你自己的地址：每笔只并 8 个、最多同时挂 6 笔、" +
                            "每笔都等真正上链确认后再并下一批，自动多轮直到并到最少。",
                        color = TextSecondary, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "• 全程只转给你自己，资金不会离开钱包\n" +
                            "• 合并期间可用余额暂时变少属正常，确认后会回来\n" +
                            "• 请保持 App 在前台、勿锁屏、勿中途重启",
                        color = TextDim, fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            StatusBlock(state)
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { showConfirm = true },
                enabled = !running,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (running) "合并进行中…" else "开始合并 Start", fontSize = 16.sp)
            }

            if (state is ConsolidateUiState.NeedsRescan) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRescan,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("一键重扫修复 Rescan", fontSize = 15.sp)
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("开始合并零钱？") },
            text = { Text("将把碎零钱分批并回你自己的地址（每笔网络费 0.0001 DRGX）。请保持前台、勿锁屏。") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onStart() }) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun StatusBlock(state: ConsolidateUiState) {
    val text = when (state) {
        is ConsolidateUiState.Idle -> "点击下方按钮开始（需先完成同步）"
        is ConsolidateUiState.Running -> "已确认 ${state.confirmed} 笔 · 已提交 ${state.submitted} 笔（等待上链…）"
        is ConsolidateUiState.Done ->
            if (state.confirmed >= state.submitted) "合并完成！共确认 ${state.confirmed} 笔"
            else "已确认 ${state.confirmed} 笔，另有 ${state.submitted - state.confirmed} 笔等待上链确认"
        is ConsolidateUiState.Nothing -> "零钱已经很整齐，无需合并"
        is ConsolidateUiState.NeedsRescan -> "检测到「链上有钱但见证未同步好」，请用下方「一键重扫修复」"
        is ConsolidateUiState.Error -> "合并出错：${state.message}"
    }
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard2, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state is ConsolidateUiState.Running) {
                Box(modifier = Modifier.size(20.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = DragonXGreen, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(text, color = TextPrimary, fontSize = 14.sp)
        }
    }
}
