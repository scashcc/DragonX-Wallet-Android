package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/**
 * Birthday-height picker with guidance. New wallets should use the latest height; old wallets should
 * estimate from roughly when they were created (year/month). Leaving it at 0/blank forces a
 * from-scratch scan (days on a phone) — heavily discouraged.
 */
@Composable
fun BirthdayHelpSection(
    birthday: String,
    onBirthdayChange: (String) -> Unit,
    latestHeight: Long,
) {
    var year by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }

    Column {
        Text("钱包起始高度 Birthday", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = birthday,
            onValueChange = { input -> onBirthdayChange(input.filter { it.isDigit() }) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("务必填写，别留空", color = TextDim) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(10.dp))

        Surface(shape = RoundedCornerShape(14.dp), color = SurfaceCard2, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedButton(
                    onClick = { if (latestHeight > 0) onBirthdayChange(latestHeight.toString()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("新钱包：用最新高度${if (latestHeight > 0) " (${"%,d".format(latestHeight)})" else ""}", fontSize = 13.sp) }

                Spacer(Modifier.height(12.dp))
                Text("老钱包：不知道高度？按你大概创建的年月估算 👇", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { y -> year = y.filter { it.isDigit() }.take(4) },
                        placeholder = { Text("年", color = TextDim) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.2f),
                    )
                    OutlinedTextField(
                        value = month,
                        onValueChange = { m -> month = m.filter { it.isDigit() }.take(2) },
                        placeholder = { Text("月", color = TextDim) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            estimateHeight(year, month, latestHeight)?.let { onBirthdayChange(it.toString()) }
                        },
                        modifier = Modifier.height(52.dp),
                    ) { Text("估算", fontSize = 13.sp) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(color = NegativeRed.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "⚠️ 强烈建议填写！留空或填很小的高度 = 从链头开始同步，手机上可能要好几天、极度卡顿。" +
                    "估算时宁可往早填一点（多扫点没关系），也别填晚了——填晚会漏掉那之前的交易和余额。",
                color = NegativeRed, fontSize = 12.sp, modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/** Rough block-height estimate from a creation year/month. Errs early (safe) with a 1-month margin. */
private fun estimateHeight(yearStr: String, monthStr: String, latestHeight: Long): Long? {
    if (latestHeight <= 0L) return null
    val y = yearStr.toIntOrNull() ?: return null
    val m = (monthStr.toIntOrNull() ?: return null).coerceIn(1, 12)
    val cal = Calendar.getInstance()
    val nowY = cal.get(Calendar.YEAR)
    val nowM = cal.get(Calendar.MONTH) + 1
    val monthsAgo = (nowY * 12 + nowM) - (y * 12 + m)
    if (monthsAgo <= 0) return latestHeight
    val blocksPerMonth = 34_560L // ~30 days at ~75s/block
    val est = latestHeight - monthsAgo.toLong() * blocksPerMonth - blocksPerMonth // extra month of margin
    return est.coerceAtLeast(1L)
}
