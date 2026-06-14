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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Send-flow phase, owned by the Fragment and observed by [SendScreen]. */
sealed interface SendPhase {
    object Editing : SendPhase
    data class Failed(val message: String) : SendPhase
    object Sending : SendPhase
    object Submitted : SendPhase
}

@Composable
fun SendScreen(
    availableText: String,
    maxAmount: String,
    phase: SendPhase,
    onBack: () -> Unit,
    onSend: (address: String, amount: String, memo: String) -> Unit,
    onDone: () -> Unit,
) {
    when (phase) {
        is SendPhase.Sending -> SendBusy()
        is SendPhase.Submitted -> SendSubmitted(onDone)
        else -> SendForm(availableText, maxAmount, phase, onBack, onSend)
    }
}

@Composable
private fun SendForm(
    availableText: String,
    maxAmount: String,
    phase: SendPhase,
    onBack: () -> Unit,
    onSend: (String, String, String) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            ScreenHeader("转账 Send", onBack)
            Spacer(Modifier.height(20.dp))

            if (phase is SendPhase.Failed) {
                Surface(color = NegativeRed.copy(alpha = 0.14f), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(phase.message, color = NegativeRed, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
                Spacer(Modifier.height(14.dp))
            }

            Text("接收地址 Address", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("zs… 或 t… 地址", color = TextDim) },
                singleLine = false,
                maxLines = 3,
                trailingIcon = {
                    TextButton(onClick = { clipboard.getText()?.text?.let { address = it.trim() } }) {
                        Text("粘贴", color = DragonXGreen, fontSize = 13.sp)
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("金额 Amount", color = TextSecondary, fontSize = 13.sp)
                Text("可用 $availableText DRGX", color = TextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0.0", color = TextDim) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    TextButton(onClick = { amount = maxAmount }) {
                        Text("全部", color = DragonXGreen, fontSize = 13.sp)
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            Text("备注 Memo（可选，仅 zs 地址支持）", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("给收款方的留言", color = TextDim) },
                singleLine = false,
                maxLines = 3,
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { onSend(address, amount, memo) },
                enabled = address.isNotBlank() && amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text("确认发送 Send", fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "网络费 0.0001 DRGX。隐私交易需等待区块确认（在交易记录中查看），" +
                    "「已提交」不代表已上链。",
                color = TextDim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SendBusy() {
    GradientBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = DragonXGreen)
            Spacer(Modifier.height(20.dp))
            Text("正在生成并提交交易…", color = TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text("隐私交易的零知识证明需要一些时间，请勿退出", color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SendSubmitted(onDone: () -> Unit) {
    GradientBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DragonXGreen, modifier = Modifier.height(64.dp))
            Spacer(Modifier.height(18.dp))
            Text("交易已提交", color = TextPrimary, fontSize = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "交易已提交到网络。请到「交易记录」查看上链确认（confirmations ≥ 1 才算真正到账）。",
                color = TextSecondary, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("完成 Done", fontSize = 16.sp)
            }
        }
    }
}
