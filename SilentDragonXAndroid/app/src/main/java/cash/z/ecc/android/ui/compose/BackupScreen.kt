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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BackupScreen(
    words: List<String>,
    birthday: String,
    onCopy: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            ScreenHeader("备份助记词 Backup", onBack)
            Spacer(Modifier.height(12.dp))

            if (words.isEmpty()) {
                Surface(shape = RoundedCornerShape(18.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "此钱包由私钥恢复，没有助记词。\n请到「我的 → 导出私钥」妥善保管你的私钥。",
                        color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(18.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("返回 Back") }
                return@Column
            }

            Surface(color = NegativeRed.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "⚠️ 请按顺序抄写在纸上、离线保管。任何人拿到这 24 个词就能转走你的资金——切勿截图、上传、告诉他人。",
                    color = NegativeRed, fontSize = 13.sp, modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            // 6 rows × 4 numbered words
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val rows = words.chunked(4)
                    rows.forEachIndexed { rowIdx, row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { colIdx, word ->
                                val number = rowIdx * 4 + colIdx + 1
                                WordCell(number, word, Modifier.weight(1f))
                            }
                        }
                        if (rowIdx != rows.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("生日高度 Birthday: $birthday（恢复时填它可加快同步）", color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))

            OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("复制助记词（请谨慎）", fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("我已安全备份 Done", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun WordCell(number: Int, word: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceCard2, modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(20.dp)) {
                Text("$number", color = TextDim, fontSize = 11.sp)
            }
            Text(word, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
