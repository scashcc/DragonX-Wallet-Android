package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileScreen(
    walletLabel: String,
    address: String,
    version: String,
    latestVersion: String?,
    hasUpdate: Boolean,
    backgroundSyncEnabled: Boolean,
    onToggleBackgroundSync: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSwitchWallet: () -> Unit,
    onBackup: () -> Unit,
    onExportKeys: () -> Unit,
    onChooseNode: () -> Unit,
    onConsolidate: () -> Unit,
    onRescan: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onDownloadApk: () -> Unit,
    onExportLog: () -> Unit,
) {
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 50.dp, bottom = 28.dp),
        ) {
            ScreenHeader("我的 Settings", onBack)
            Spacer(Modifier.height(20.dp))

            Surface(shape = RoundedCornerShape(20.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(walletLabel, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(address, color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(22.dp))
            GroupTitle("钱包 Wallet")
            SettingsCard {
                SettingsRow(Icons.Filled.Person, "切换 / 新建钱包", "管理多个钱包，各自独立", onSwitchWallet)
                RowDivider()
                SettingsRow(Icons.Filled.Lock, "备份助记词", "查看 24 个助记词（需身份验证）", onBackup)
                RowDivider()
                SettingsRow(Icons.Filled.ExitToApp, "导出私钥", "导出 Sapling 私钥 / 地址（需身份验证）", onExportKeys)
            }

            Spacer(Modifier.height(18.dp))
            GroupTitle("网络与维护 Network")
            SettingsCard {
                SettingsRow(Icons.Filled.Settings, "选择节点", "查看延迟/区块高度并切换节点", onChooseNode)
                RowDivider()
                SettingsRow(Icons.Filled.Refresh, "合并零钱", "把碎零钱并成大额，便于转账", onConsolidate)
                RowDivider()
                SettingsRow(Icons.Filled.Refresh, "重扫钱包", "同步异常时从链上重建", onRescan)
            }

            Spacer(Modifier.height(18.dp))
            GroupTitle("后台 Background")
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Filled.Refresh,
                    title = "后台同步",
                    subtitle = "常驻通知保持后台同步，防止系统杀进程导致区块库损坏。" +
                        "小米/华为等需在系统里给本应用开「自启动」并关闭「省电策略」。",
                    checked = backgroundSyncEnabled,
                    onChange = onToggleBackgroundSync,
                )
            }

            Spacer(Modifier.height(18.dp))
            GroupTitle("诊断 Diagnostics")
            SettingsCard {
                SettingsRow(
                    Icons.Filled.ExitToApp,
                    "导出 / 分享日志",
                    "转账或同步出问题时，把日志发给开发者帮你排查",
                    onExportLog,
                )
            }

            Spacer(Modifier.height(26.dp))
            UpdateFooter(
                version = version,
                latestVersion = latestVersion,
                hasUpdate = hasUpdate,
                onCheckUpdate = onCheckUpdate,
                onOpenReleasePage = onOpenReleasePage,
                onDownloadApk = onDownloadApk,
            )
        }
    }
}

/**
 * Bottom-of-settings version line + reminder-only update affordance. Shows current vs latest; when a
 * newer build exists it offers "在线更新" (open the release page) and "下载 APK" (direct link). Nothing
 * downloads/installs automatically — the user decides.
 */
@Composable
private fun UpdateFooter(
    version: String,
    latestVersion: String?,
    hasUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onDownloadApk: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "DragonX Wallet · 当前版本 v$version",
            color = TextDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        when {
            latestVersion == null -> {
                // Not checked yet, or the check failed (e.g. api.github.com unreachable).
                Surface(
                    onClick = onCheckUpdate,
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceCard,
                ) {
                    Text(
                        "点击检查更新",
                        color = DragonXGreen,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            hasUpdate -> {
                Text(
                    "发现新版本 v$latestVersion",
                    color = WarnAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onOpenReleasePage) { Text("在线更新", fontSize = 14.sp) }
                    Button(onClick = onDownloadApk) { Text("下载 APK", fontSize = 14.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "下载后请手动安装覆盖更新，助记词不受影响。",
                    color = TextDim,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Text("已是最新版本 ✓ (v$latestVersion)", color = PositiveGreen, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 64.dp)
            .height(1.dp)
            .background(StrokeSubtle),
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = DragonXGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, color = TextDim, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = DragonXGreen),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = DragonXGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, color = TextDim, fontSize = 12.sp)
                }
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = TextDim)
        }
    }
}
