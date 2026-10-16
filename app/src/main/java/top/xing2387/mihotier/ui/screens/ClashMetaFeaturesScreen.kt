package top.xing2387.mihotier.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.MetaFeaturesSettings
import top.xing2387.mihotier.core.config.MetaFeaturesSettingsRepository
import top.xing2387.mihotier.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashMetaFeaturesScreen(
    isRunning: Boolean = false,
    onRestartVpn: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var savedSettings by remember { mutableStateOf(MetaFeaturesSettingsRepository.load(context)) }
    var currentSettings by remember { mutableStateOf(savedSettings) }
    val originalSnapshot = remember { ConfigManager.getOriginalConfigSnapshot(context) }

    val hasChanges = currentSettings != savedSettings

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showProcessModeDialog by remember { mutableStateOf(false) }
    var showUnifiedDelayDialog by remember { mutableStateOf(false) }
    var showTcpConcurrentDialog by remember { mutableStateOf(false) }
    var showFingerprintDialog by remember { mutableStateOf(false) }
    var showGeodataLoaderDialog by remember { mutableStateOf(false) }
    var showGeodataModeDialog by remember { mutableStateOf(false) }

    fun doSave() {
        MetaFeaturesSettingsRepository.save(context, currentSettings)
        savedSettings = currentSettings
        if (isRunning) {
            onRestartVpn()
            Toast.makeText(context, "Meta 特性已保存，正在重启代理生效", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Meta 特性设置已保存", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleBack() {
        if (hasChanges) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = hasChanges) {
        handleBack()
    }

    val origSniffer = originalSnapshot.snifferEnabled ?: "未配置"
    val origProcessMode = originalSnapshot.findProcessMode ?: "未配置"
    val origUnifiedDelay = originalSnapshot.unifiedDelay ?: "false"
    val origTcpConcurrent = originalSnapshot.tcpConcurrent ?: "false"
    val origFingerprint = originalSnapshot.globalClientFingerprint ?: "未配置"
    val origGeoLoader = originalSnapshot.geodataLoader ?: "memconservative"
    val origGeoMode = originalSnapshot.geodataMode ?: "false"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Meta 特性 (Meta Features)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showResetConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Defaults",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                    IconButton(onClick = { doSave() }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (hasChanges) AppTheme.colors.brandPrimary else AppTheme.colors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.background
                )
            )
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Master Meta Features Switch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                MetaCardSwitchItem(
                    icon = Icons.Default.AutoAwesome,
                    iconBg = Color(0xFFFBCFE8),
                    iconTint = Color(0xFF9D174D),
                    title = "启用 Meta 内核增强特性",
                    subtitle = "开启后将自动向 Mihomo (Clash.Meta) 内核注入流量嗅探、进程匹配及连接优化参数",
                    checked = currentSettings.enableMetaFeatures,
                    onCheckedChange = {
                        currentSettings = currentSettings.copy(enableMetaFeatures = it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Domain Sniffer
            Text(
                text = "域名流量嗅探 (Domain Sniffer)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MetaCardSwitchItem(
                        icon = Icons.Default.Radar,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "启用域名流量嗅探 (Sniffer)",
                        originalBadge = "原: $origSniffer",
                        isOverridden = currentSettings.enableMetaFeatures && currentSettings.snifferEnable,
                        subtitle = "主动探测 HTTP Host 与 TLS/QUIC SNI 域名，解决硬编码 IP 绕过规则问题",
                        checked = currentSettings.snifferEnable,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(snifferEnable = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardSwitchItem(
                        icon = Icons.Default.SwapCalls,
                        iconBg = Color(0xFFE0E7FF),
                        iconTint = Color(0xFF3730A3),
                        title = "覆盖连接目标地址 (Override Destination)",
                        subtitle = "使用嗅探到的真实域名替换连接目标 IP，交由远端代理节点解析",
                        checked = currentSettings.snifferOverrideDest,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(snifferOverrideDest = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardSwitchItem(
                        icon = Icons.Default.FindInPage,
                        iconBg = Color(0xFFA7F3D0),
                        iconTint = Color(0xFF065F46),
                        title = "强制 Fake-IP 域名映射嗅探",
                        subtitle = "对命中 Fake-IP 池 (198.18.0.1/16) 的流量强制执行域名还原与分流",
                        checked = currentSettings.snifferForceDnsMapping,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(snifferForceDnsMapping = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardSwitchItem(
                        icon = Icons.Default.Public,
                        iconBg = Color(0xFFCCFBF1),
                        iconTint = Color(0xFF0F766E),
                        title = "纯 IP 流量嗅探 (Parse Pure IP)",
                        subtitle = "对所有未经过内置 DNS 解析的直连 IP 流量尝试提取域名并匹配规则",
                        checked = currentSettings.snifferParsePureIp,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(snifferParsePureIp = it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Process & Connection Optimization
            Text(
                text = "进程匹配与连接加速 (Process & Transport)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MetaCardNavigationItem(
                        icon = Icons.Default.AccountTree,
                        iconBg = Color(0xFFFED7AA),
                        iconTint = Color(0xFF9A3412),
                        title = "进程匹配模式 (Find Process Mode)",
                        originalBadge = "原: $origProcessMode",
                        isOverridden = currentSettings.enableMetaFeatures && currentSettings.findProcessMode != "Keep",
                        subtitle = formatProcessModeLabel(currentSettings.findProcessMode, origProcessMode),
                        onClick = { showProcessModeDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardNavigationItem(
                        icon = Icons.Default.Speed,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "统一延迟计算 (Unified Delay)",
                        originalBadge = "原: $origUnifiedDelay",
                        isOverridden = currentSettings.enableMetaFeatures && currentSettings.unifiedDelay != "Keep",
                        subtitle = formatMetaTriStateLabel(
                            currentSettings.unifiedDelay,
                            origUnifiedDelay,
                            "消除不同协议握手差异，测量真实 RTT 延迟"
                        ),
                        onClick = { showUnifiedDelayDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardNavigationItem(
                        icon = Icons.Default.Bolt,
                        iconBg = Color(0xFFA7F3D0),
                        iconTint = Color(0xFF065F46),
                        title = "TCP 并发连接 (TCP Concurrent)",
                        originalBadge = "原: $origTcpConcurrent",
                        isOverridden = currentSettings.enableMetaFeatures && currentSettings.tcpConcurrent != "Keep",
                        subtitle = formatMetaTriStateLabel(
                            currentSettings.tcpConcurrent,
                            origTcpConcurrent,
                            "向多 IP 并发发起 TCP 握手并选取最快连接"
                        ),
                        onClick = { showTcpConcurrentDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardNavigationItem(
                        icon = Icons.Default.Fingerprint,
                        iconBg = Color(0xFFFBCFE8),
                        iconTint = Color(0xFF9D174D),
                        title = "全局 TLS 客户端指纹 (uTLS Fingerprint)",
                        originalBadge = "原: $origFingerprint",
                        isOverridden = currentSettings.enableMetaFeatures && currentSettings.globalClientFingerprint != "Keep",
                        subtitle = formatFingerprintLabel(currentSettings.globalClientFingerprint, origFingerprint),
                        onClick = { showFingerprintDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: GeoData & Profile Store
            Text(
                text = "Geo 数据库与状态缓存 (GeoData & Cache)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MetaCardNavigationItem(
                        icon = Icons.Default.Memory,
                        iconBg = Color(0xFFE0E7FF),
                        iconTint = Color(0xFF3730A3),
                        title = "Geo 数据库加载器 (GeoData Loader)",
                        originalBadge = "原: $origGeoLoader",
                        isOverridden = currentSettings.enableMetaFeatures && currentSettings.geodataLoader != "Keep",
                        subtitle = formatGeoLoaderLabel(currentSettings.geodataLoader, origGeoLoader),
                        onClick = { showGeodataLoaderDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardNavigationItem(
                        icon = Icons.Default.Storage,
                        iconBg = Color(0xFFCCFBF1),
                        iconTint = Color(0xFF0F766E),
                        title = "GeoData 数据库格式 (GeoData Mode)",
                        originalBadge = "原: $origGeoMode",
                        isOverridden = currentSettings.enableMetaFeatures && currentSettings.geodataMode != "Keep",
                        subtitle = formatMetaTriStateLabel(
                            currentSettings.geodataMode,
                            origGeoMode,
                            "开启使用 .dat 格式，关闭使用 .mmdb 格式"
                        ),
                        onClick = { showGeodataModeDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardSwitchItem(
                        icon = Icons.Default.Update,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "自动更新 Geo 数据库 (Geo Auto Update)",
                        subtitle = "定期在后台自动检查并更新 GeoIP 与 GeoSite 分流规则库",
                        checked = currentSettings.geoAutoUpdate,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(geoAutoUpdate = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardSwitchItem(
                        icon = Icons.Default.History,
                        iconBg = Color(0xFFFED7AA),
                        iconTint = Color(0xFF9A3412),
                        title = "记忆策略组节点选择 (Store Selected)",
                        subtitle = "重启应用或重连 VPN 后自动恢复上次选中的代理节点",
                        checked = currentSettings.storeSelected,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(storeSelected = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    MetaCardSwitchItem(
                        icon = Icons.Default.Cached,
                        iconBg = Color(0xFFA7F3D0),
                        iconTint = Color(0xFF065F46),
                        title = "持久化 Fake-IP 缓存 (Store Fake-IP)",
                        subtitle = "将 Fake-IP 域名映射缓存写入磁盘，加速重启后的首次连接解析",
                        checked = currentSettings.storeFakeIp,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(storeFakeIp = it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Save Button
            Button(
                onClick = { doSave() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.brandPrimary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    text = if (hasChanges) "保存并应用 Meta 特性" else "保存 Meta 特性设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Find Process Mode Dialog
        if (showProcessModeDialog) {
            MetaRadioDialog(
                title = "进程匹配模式 (Find Process Mode)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origProcessMode)",
                    "always" to "始终开启 (always · 支持按应用包名分流)",
                    "strict" to "严格模式 (strict · 仅匹配明确本地进程)",
                    "off" to "完全关闭 (off · 禁用进程识别以省电)"
                ),
                selectedValue = currentSettings.findProcessMode,
                onSelect = {
                    currentSettings = currentSettings.copy(findProcessMode = it)
                    showProcessModeDialog = false
                },
                onDismiss = { showProcessModeDialog = false }
            )
        }

        // Unified Delay Dialog
        if (showUnifiedDelayDialog) {
            MetaRadioDialog(
                title = "统一延迟计算 (Unified Delay)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origUnifiedDelay)",
                    "true" to "开启 (true · 推荐，反映真实网络 RTT)",
                    "false" to "关闭 (false · 包含握手耗时)"
                ),
                selectedValue = currentSettings.unifiedDelay,
                onSelect = {
                    currentSettings = currentSettings.copy(unifiedDelay = it)
                    showUnifiedDelayDialog = false
                },
                onDismiss = { showUnifiedDelayDialog = false }
            )
        }

        // TCP Concurrent Dialog
        if (showTcpConcurrentDialog) {
            MetaRadioDialog(
                title = "TCP 并发连接 (TCP Concurrent)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origTcpConcurrent)",
                    "true" to "开启 (true · 多 IP 并发择优连接)",
                    "false" to "关闭 (false · 按顺序依次连接)"
                ),
                selectedValue = currentSettings.tcpConcurrent,
                onSelect = {
                    currentSettings = currentSettings.copy(tcpConcurrent = it)
                    showTcpConcurrentDialog = false
                },
                onDismiss = { showTcpConcurrentDialog = false }
            )
        }

        // Global Client Fingerprint Dialog
        if (showFingerprintDialog) {
            MetaRadioDialog(
                title = "全局 TLS 客户端指纹 (uTLS)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origFingerprint)",
                    "chrome" to "Chrome 浏览器指纹 (chrome)",
                    "firefox" to "Firefox 浏览器指纹 (firefox)",
                    "safari" to "Safari 浏览器指纹 (safari)",
                    "ios" to "iOS 系统指纹 (ios)",
                    "android" to "Android 系统指纹 (android)",
                    "edge" to "Edge 浏览器指纹 (edge)",
                    "random" to "随机切换指纹 (random)"
                ),
                selectedValue = currentSettings.globalClientFingerprint,
                onSelect = {
                    currentSettings = currentSettings.copy(globalClientFingerprint = it)
                    showFingerprintDialog = false
                },
                onDismiss = { showFingerprintDialog = false }
            )
        }

        // GeoData Loader Dialog
        if (showGeodataLoaderDialog) {
            MetaRadioDialog(
                title = "Geo 数据库加载器 (GeoData Loader)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origGeoLoader)",
                    "memconservative" to "低内存模式 (memconservative · 推荐手机端)",
                    "standard" to "标准高速模式 (standard · 占用更多内存)"
                ),
                selectedValue = currentSettings.geodataLoader,
                onSelect = {
                    currentSettings = currentSettings.copy(geodataLoader = it)
                    showGeodataLoaderDialog = false
                },
                onDismiss = { showGeodataLoaderDialog = false }
            )
        }

        // GeoData Mode Dialog
        if (showGeodataModeDialog) {
            MetaRadioDialog(
                title = "GeoData 数据库格式 (GeoData Mode)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origGeoMode)",
                    "true" to "开启 (true · 使用 GeoIP.dat 格式)",
                    "false" to "关闭 (false · 使用 Country.mmdb 格式)"
                ),
                selectedValue = currentSettings.geodataMode,
                onSelect = {
                    currentSettings = currentSettings.copy(geodataMode = it)
                    showGeodataModeDialog = false
                },
                onDismiss = { showGeodataModeDialog = false }
            )
        }

        // Reset Confirmation Dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                containerColor = AppTheme.colors.cardBackground,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "恢复默认 Meta 特性",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "确定要将所有 Meta 内核特性恢复为推荐默认状态吗？",
                        fontSize = 14.sp,
                        color = AppTheme.colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        currentSettings = MetaFeaturesSettings()
                        showResetConfirmDialog = false
                    }) {
                        Text("恢复默认", color = AppTheme.colors.statusDangerText, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text("取消", color = AppTheme.colors.textSecondary)
                    }
                }
            )
        }

        // Unsaved Changes Dialog
        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                containerColor = AppTheme.colors.cardBackground,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "未保存的修改",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "当前 Meta 特性设置有未保存的更改，是否在离开前保存？",
                        fontSize = 14.sp,
                        color = AppTheme.colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        doSave()
                        onNavigateBack()
                    }) {
                        Text("保存并退出", color = AppTheme.colors.brandPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showUnsavedDialog = false }) {
                            Text("取消", color = AppTheme.colors.textSecondary)
                        }
                        TextButton(onClick = {
                            showUnsavedDialog = false
                            onNavigateBack()
                        }) {
                            Text("不保存", color = AppTheme.colors.statusDangerText)
                        }
                    }
                }
            )
        }
    }
}

private fun formatProcessModeLabel(mode: String, orig: String): String {
    return when (mode) {
        "always" -> "已设为: 始终开启 (always) · 支持 PROCESS-NAME 分应用规则"
        "strict" -> "已设为: 严格模式 (strict) · 原配置: $orig"
        "off" -> "已设为: 完全关闭 (off) · 原配置: $orig"
        else -> "不覆写 · 沿用原配置 ($orig)"
    }
}

private fun formatMetaTriStateLabel(value: String, orig: String, desc: String): String {
    return when (value) {
        "true" -> "已设为: 开启 (true) · $desc"
        "false" -> "已设为: 关闭 (false) · $desc"
        else -> "不覆写 · 沿用原配置 ($orig) · $desc"
    }
}

private fun formatFingerprintLabel(fp: String, orig: String): String {
    return if (fp == "Keep" || fp.isBlank()) {
        "不覆写 · 沿用原配置 ($orig)"
    } else {
        "已设为: $fp 客户端 TLS 指纹 · 原配置: $orig"
    }
}

private fun formatGeoLoaderLabel(loader: String, orig: String): String {
    return when (loader) {
        "memconservative" -> "已设为: 低内存优化模式 (memconservative)"
        "standard" -> "已设为: 标准高速模式 (standard)"
        else -> "不覆写 · 沿用原配置 ($orig)"
    }
}

@Composable
private fun MetaRadioDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.cardBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
        },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedValue == value),
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AppTheme.colors.brandPrimary,
                                unselectedColor = AppTheme.colors.textMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = label,
                            fontSize = 14.5.sp,
                            color = AppTheme.colors.textPrimary,
                            fontWeight = if (selectedValue == value) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppTheme.colors.textSecondary)
            }
        }
    )
}

@Composable
private fun MetaCardSwitchItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    originalBadge: String? = null,
    isOverridden: Boolean = false,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (originalBadge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOverridden) AppTheme.colors.statusInfoBg else AppTheme.colors.surfaceSubtle
                    ) {
                        Text(
                            text = originalBadge,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOverridden) AppTheme.colors.statusInfoText else AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = AppTheme.colors.textSecondary,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppTheme.colors.brandPrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = AppTheme.colors.cardBorder
            )
        )
    }
}

@Composable
private fun MetaCardNavigationItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    originalBadge: String? = null,
    isOverridden: Boolean = false,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (originalBadge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOverridden) AppTheme.colors.statusInfoBg else AppTheme.colors.surfaceSubtle
                    ) {
                        Text(
                            text = originalBadge,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOverridden) AppTheme.colors.statusInfoText else AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = AppTheme.colors.textSecondary,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Navigate",
            tint = AppTheme.colors.textMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
