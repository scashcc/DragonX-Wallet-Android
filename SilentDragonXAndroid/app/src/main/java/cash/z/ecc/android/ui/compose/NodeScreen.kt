package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One selectable node and its measured latency. latencyMs: null=probing, <0=offline. */
data class NodeStatus(
    val host: String,
    val latencyMs: Long?,
    val isCurrent: Boolean,
)

@Composable
fun NodeScreen(
    nodes: List<NodeStatus>,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
) {
    GradientBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 50.dp)) {
                ScreenHeader("选择节点 Node", onBack)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(PositiveGreen, "快速")
                    Legend(WarnAmber, "一般")
                    Legend(NegativeRed, "慢 / 离线")
                }
                Spacer(Modifier.height(6.dp))
                Text("点击任一节点即可切换（无需重启）。延迟越低、越稳定。", color = TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(nodes) { node ->
                    NodeRow(node) { onSelect(node.host) }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun NodeRow(node: NodeStatus, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(node.host, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (node.isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Text("● 当前", color = DragonXGreen, fontSize = 12.sp)
                    }
                }
                Text(latencyText(node.latencyMs), color = TextDim, fontSize = 12.sp)
            }
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(latencyColor(node.latencyMs)))
        }
    }
}

private fun latencyText(ms: Long?): String = when {
    ms == null -> "检测中…"
    ms < 0 -> "离线 / 无法连接"
    else -> "$ms ms"
}

private fun latencyColor(ms: Long?): Color = when {
    ms == null -> TextDim
    ms < 0 -> NegativeRed
    ms < 500 -> PositiveGreen
    ms < 1500 -> WarnAmber
    else -> NegativeRed
}
