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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.z.ecc.android.ext.WalletZecFormmatter
import cash.z.ecc.android.sdk.db.entity.ConfirmedTransaction
import cash.z.ecc.android.sdk.db.entity.valueInZatoshi
import cash.z.ecc.android.sdk.ext.ZcashSdk
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.ui.util.toUtf8Memo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionDetailScreen(
    tx: ConfirmedTransaction?,
    latestHeight: Long?,
    onBack: () -> Unit,
    onCopyTxid: (String) -> Unit,
) {
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            ScreenHeader("交易详情 Detail", onBack)
            Spacer(Modifier.height(24.dp))

            if (tx == null) {
                Text("未选择交易", color = TextDim, fontSize = 14.sp)
                return@Column
            }

            val inbound = tx.toAddress.isNullOrEmpty()
            val amount = WalletZecFormmatter.toZecStringFull(tx.valueInZatoshi)
            val sign = if (inbound) "+" else "−"
            val color = if (inbound) PositiveGreen else NegativeRed
            val txid = toTxId(tx.rawTransactionId)
            val memo = runCatching { tx.memo.toUtf8Memo() }.getOrNull()?.takeIf { it.isNotBlank() }

            // Headline amount + status
            Surface(shape = RoundedCornerShape(22.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (inbound) "收款 Received" else "转账 Sent", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("$sign$amount DRGX", color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    val (statusText, statusColor) = confirmationStatus(tx.minedHeight, latestHeight, tx.blockTimeInSeconds)
                    Pill(statusText, dotColor = statusColor)
                }
            }

            Spacer(Modifier.height(18.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    DetailRow("时间 Time", formatTime(tx.blockTimeInSeconds))
                    if (tx.minedHeight > 0) DetailRow("区块高度 Height", String.format(Locale.US, "%,d", tx.minedHeight))
                    if (!inbound) DetailRow("网络费 Fee", "${WalletZecFormmatter.toZecStringFull(Zatoshi(ZcashSdk.MINERS_FEE.value))} DRGX")
                    if (!inbound && !tx.toAddress.isNullOrEmpty()) DetailRow("收款地址 To", tx.toAddress!!, mono = true)
                    if (memo != null) DetailRow("备注 Memo", memo)
                    if (txid != null) DetailRow("交易 ID", txid, mono = true, onClick = { onCopyTxid(txid) }, hint = "点击复制")
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "隐私交易在区块浏览器上查不到属正常；以确认数为准。",
                color = TextDim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
    onClick: (() -> Unit)? = null,
    hint: String? = null,
) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, fontSize = 13.sp)
            if (onClick != null && hint != null) {
                TextButton(onClick = onClick) { Text(hint, color = DragonXGreen, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = TextPrimary,
            fontSize = if (mono) 12.sp else 14.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

private fun confirmationStatus(minedHeight: Int, latestHeight: Long?, blockTimeInSeconds: Long): Pair<String, androidx.compose.ui.graphics.Color> {
    if (blockTimeInSeconds <= 0L || minedHeight <= 0) return "待确认 Pending" to WarnAmber
    if (latestHeight == null || latestHeight <= 0L) return "已上链 Mined" to PositiveGreen
    val confirmations = (latestHeight - minedHeight + 1).coerceAtLeast(0)
    return if (confirmations >= 10) "已确认 Confirmed ($confirmations)" to PositiveGreen
    else "确认中 $confirmations/10" to WarnAmber
}

private fun formatTime(seconds: Long): String {
    if (seconds <= 0L) return "—"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(seconds * 1000))
}

private fun toTxId(raw: ByteArray?): String? {
    if (raw == null) return null
    val sb = StringBuilder(raw.size * 2)
    for (i in raw.indices.reversed()) sb.append(String.format("%02x", raw[i]))
    return sb.toString()
}
