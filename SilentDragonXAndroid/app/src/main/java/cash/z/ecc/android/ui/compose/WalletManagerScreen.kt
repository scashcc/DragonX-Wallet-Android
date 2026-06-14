package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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

data class WalletItem(val index: Int, val label: String, val isActive: Boolean)

@Composable
fun WalletManagerScreen(
    wallets: List<WalletItem>,
    busy: Boolean,
    busyText: String,
    onSwitch: (Int) -> Unit,
    onCreate: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("钱包 ${wallets.size + 1}") }

    if (busy) {
        GradientBackground {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = DragonXGreen)
                Spacer(Modifier.height(16.dp))
                Text(busyText, color = TextSecondary, fontSize = 14.sp)
            }
        }
        return
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            ScreenHeader("钱包 Wallets", onBack)
            Spacer(Modifier.height(8.dp))
            Text("每个钱包独立地址、独立同步数据。点击切换。", color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))

            wallets.forEach { w ->
                Surface(
                    onClick = { if (!w.isActive) onSwitch(w.index) },
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceCard,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(w.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (w.isActive) {
                            Text("● 当前", color = DragonXGreen, fontSize = 13.sp)
                        } else {
                            Text("切换 →", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { newLabel = "钱包 ${wallets.size + 1}"; showNew = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("＋ 新建钱包", fontSize = 16.sp) }
            Spacer(Modifier.height(12.dp))
            Text(
                "⚠️ 新建后请立即到「我的 → 备份助记词」备份它专属的 24 个助记词；建议先用空钱包测试切换。",
                color = TextDim, fontSize = 12.sp,
            )
        }
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("新建钱包") },
            text = {
                Column {
                    Text("会生成一套全新的 24 词助记词与独立地址。", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showNew = false
                    onCreate(newLabel.ifBlank { "钱包 ${wallets.size + 1}" })
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("取消") } },
        )
    }
}
