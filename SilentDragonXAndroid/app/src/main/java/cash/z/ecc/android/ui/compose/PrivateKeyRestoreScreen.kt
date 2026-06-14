package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrivateKeyRestoreScreen(
    busy: Boolean,
    error: String?,
    latestHeight: Long,
    onBack: () -> Unit,
    onRestore: (key: String, birthday: String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            ScreenHeader("私钥恢复 Private Key", onBack)
            Spacer(Modifier.height(16.dp))
            Text(
                "粘贴你的 Sapling 私钥（以 secret-extended-key-main 开头）即可恢复钱包。" +
                    "私钥可在另一个 DragonX 钱包的「导出私钥」里获得。",
                color = TextSecondary, fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))

            Text("私钥 Spending key", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("secret-extended-key-main1…", color = TextDim) },
                singleLine = false,
                maxLines = 5,
                trailingIcon = {
                    TextButton(onClick = { clipboard.getText()?.text?.let { key = it.trim() } }) {
                        Text("粘贴", color = DragonXGreen, fontSize = 13.sp)
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            BirthdayHelpSection(
                birthday = birthday,
                onBirthdayChange = { birthday = it },
                latestHeight = latestHeight,
            )

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(error, color = NegativeRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(26.dp))
            if (busy) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    CircularProgressIndicator(color = DragonXGreen)
                }
            } else {
                Button(
                    onClick = { onRestore(key, birthday) },
                    enabled = key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("恢复钱包 Restore", fontSize = 16.sp) }
            }
        }
    }
}
