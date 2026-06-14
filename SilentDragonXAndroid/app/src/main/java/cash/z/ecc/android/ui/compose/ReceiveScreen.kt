package cash.z.ecc.android.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun ReceiveScreen(
    address: String?,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            ScreenHeader("收款 Receive", onBack)
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "扫码或复制下方地址，即可向此钱包转入 DRGX",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    val qr = remember(address) { address?.let { encodeQrBitmap(it, 600) } }
                    Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                        Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                            if (qr != null) {
                                Image(
                                    bitmap = qr.asImageBitmap(),
                                    contentDescription = "收款二维码",
                                    modifier = Modifier.size(220.dp),
                                )
                            } else {
                                Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                                    Text("地址加载中…", color = TextDim, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        address ?: "—",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("复制地址") }
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("分享") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "仅可接收 DragonX (DRGX) 隐私地址 zs… 的转账。",
                color = TextDim,
                fontSize = 12.sp,
            )
        }
    }
}

/** Encode [text] to a square QR bitmap with zxing (the app already depends on com.google.zxing:core). */
private fun encodeQrBitmap(text: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val w = matrix.width
    val h = matrix.height
    val black = android.graphics.Color.BLACK
    val white = android.graphics.Color.WHITE
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            pixels[y * w + x] = if (matrix.get(x, y)) black else white
        }
    }
    Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
}.getOrNull()
