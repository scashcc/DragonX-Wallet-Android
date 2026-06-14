package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.z.ecc.android.ext.WalletZecFormmatter
import cash.z.ecc.android.sdk.db.entity.ConfirmedTransaction
import cash.z.ecc.android.sdk.db.entity.valueInZatoshi
import cash.z.ecc.android.ui.util.toUtf8Memo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One transaction row (inbound = received, outbound = sent), used by History and the Home recents. */
@Composable
fun TxRow(tx: ConfirmedTransaction) {
    val inbound = tx.toAddress.isNullOrEmpty()
    val amount = WalletZecFormmatter.toZecStringShort(tx.valueInZatoshi)
    val sign = if (inbound) "+" else "−"
    val amountColor = if (inbound) PositiveGreen else NegativeRed
    val title = if (inbound) "收款 Received" else "转账 Sent"
    val time = formatTxTime(tx.blockTimeInSeconds)
    val memo = runCatching { tx.memo.toUtf8Memo() }.getOrNull()?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (inbound) PositiveGreen.copy(alpha = 0.16f) else NegativeRed.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (inbound) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = if (inbound) PositiveGreen else NegativeRed,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(time, color = TextDim, fontSize = 12.sp)
            if (memo != null) {
                Text("备注: $memo", color = TextDim, fontSize = 12.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("$sign$amount", color = amountColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("DRGX", color = TextDim, fontSize = 11.sp)
        }
    }
}

private fun formatTxTime(seconds: Long): String {
    if (seconds <= 0L) return "待确认 Pending"
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000))
}
