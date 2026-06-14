package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RescanScreen(
    quickInfo: String,
    fullInfo: String,
    onQuick: () -> Unit,
    onFull: () -> Unit,
    onWipe: () -> Unit,
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
            ScreenHeader("重扫钱包 Rescan", onBack)
            Spacer(Modifier.height(8.dp))
            Text(
                "余额/交易显示异常时，重新扫描区块以重建钱包状态。助记词/密钥不受影响。",
                color = TextDim, fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))

            OptionCard("快速重扫 Quick", "回退约一周的区块重新扫描，最快", quickInfo, DragonXGreen, onQuick)
            Spacer(Modifier.height(12.dp))
            OptionCard("完整重扫 Full", "从钱包生日完整重新扫描，最彻底（较久）", fullInfo, DragonXBlue, onFull)
            Spacer(Modifier.height(12.dp))
            OptionCard("清除并重新同步 Reset", "删除本地区块数据从头同步，修复同步卡死 / 数据库损坏", "助记词/密钥不受影响，App 会自动重启", WarnAmber, onWipe)
        }
    }
}

@Composable
private fun OptionCard(
    title: String,
    subtitle: String,
    info: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            if (info.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(info, color = TextDim, fontSize = 12.sp)
            }
        }
    }
}
