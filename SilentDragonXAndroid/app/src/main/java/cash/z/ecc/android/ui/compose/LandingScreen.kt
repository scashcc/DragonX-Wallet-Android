package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LandingScreen(
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onRestoreSeed: () -> Unit,
    onRestorePrivateKey: () -> Unit,
) {
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(DragonXGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = BgDeep, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("DragonX 钱包", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "安全、隐私的 DragonX (DRGX) 钱包",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            if (error != null) {
                Text(error, color = NegativeRed, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }

            if (busy) {
                CircularProgressIndicator(color = DragonXGreen)
                Spacer(Modifier.height(16.dp))
                Text("正在创建钱包…", color = TextSecondary, fontSize = 14.sp)
            } else {
                Button(
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("创建新钱包 Create", fontSize = 16.sp) }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onRestoreSeed,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("用助记词恢复 (24 词)", fontSize = 15.sp) }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onRestorePrivateKey,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("用私钥恢复 (secret-extended-key)", color = TextSecondary, fontSize = 14.sp) }
            }
        }
    }
}
