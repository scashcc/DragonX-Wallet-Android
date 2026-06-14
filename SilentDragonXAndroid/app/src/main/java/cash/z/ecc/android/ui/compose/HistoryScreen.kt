package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.z.ecc.android.sdk.db.entity.ConfirmedTransaction

@Composable
fun HistoryScreen(
    transactions: List<ConfirmedTransaction>,
    synced: Boolean,
    onBack: () -> Unit,
    onTxClick: (ConfirmedTransaction) -> Unit,
) {
    var filter by remember { mutableStateOf(0) } // 0=all, 1=received, 2=sent
    val shown = remember(transactions, filter) {
        when (filter) {
            1 -> transactions.filter { it.toAddress.isNullOrEmpty() }
            2 -> transactions.filter { !it.toAddress.isNullOrEmpty() }
            else -> transactions
        }
    }
    GradientBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 50.dp)) {
                ScreenHeader("交易记录 History", onBack)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip("全部", filter == 0) { filter = 0 }
                    FilterChip("收款", filter == 1) { filter = 1 }
                    FilterChip("转账", filter == 2) { filter = 2 }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (shown.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            !synced && transactions.isEmpty() -> "同步中，交易记录会在扫描完成后显示…\nSyncing…"
                            transactions.isEmpty() -> "暂无交易记录\nNo transactions yet"
                            else -> "该类别暂无交易\nNothing in this filter"
                        },
                        color = TextDim,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown) { tx ->
                        Surface(
                            onClick = { onTxClick(tx) },
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceCard,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TxRow(tx)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) DragonXGreen else SurfaceCard2,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClick() },
    ) {
        Text(
            label,
            color = if (selected) BgDeep else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}
