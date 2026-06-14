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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Restore-from-seed (Compose). 24 independent numbered cells (any one editable in place — preserves
 * the "fix one word without shifting the rest" behaviour) plus a "paste all 24" shortcut. The word
 * collection + validation + import all delegate to the unchanged WalletSetupViewModel.
 */
@Composable
fun RestoreScreen(
    busy: Boolean,
    error: String?,
    latestHeight: Long,
    onBack: () -> Unit,
    onRestore: (words: List<String>, birthday: String) -> Unit,
) {
    val words = remember { mutableStateListOf<String>().apply { repeat(24) { add("") } } }
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
            ScreenHeader("恢复钱包 Restore", onBack)
            Spacer(Modifier.height(10.dp))
            Text("按顺序输入你的 24 个助记词，或一键粘贴。", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    clipboard.getText()?.text?.let { pasted ->
                        val parts = pasted.trim().split(Regex("\\s+"))
                        for (i in 0 until 24) words[i] = parts.getOrNull(i)?.trim()?.lowercase() ?: ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("粘贴 24 词 Paste", fontSize = 14.sp) }

            Spacer(Modifier.height(14.dp))
            for (row in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 4) {
                        val idx = row * 4 + col
                        SeedCell(
                            number = idx + 1,
                            value = words[idx],
                            onChange = { words[idx] = it.trim().lowercase() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(12.dp))
            BirthdayHelpSection(
                birthday = birthday,
                onBirthdayChange = { birthday = it },
                latestHeight = latestHeight,
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = NegativeRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(22.dp))
            if (busy) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = DragonXGreen)
                }
            } else {
                Button(
                    onClick = { onRestore(words.toList(), birthday) },
                    enabled = words.all { it.isNotBlank() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("恢复钱包 Restore", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun SeedCell(number: Int, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceCard2, modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(16.dp)) {
                Text("$number", color = TextDim, fontSize = 10.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(DragonXGreen),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
