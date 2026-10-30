package top.xing2387.mihotier.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.OriginalConfigSnapshot
import top.xing2387.mihotier.core.config.OverrideSettings
import top.xing2387.mihotier.core.config.OverrideSettingsRepository
import top.xing2387.mihotier.ui.theme.AppTheme
import top.xing2387.mihotier.ui.theme.appTextFieldColors

private enum class OverrideSubPage {
    NONE,
    RULES_EDITOR,
    HOSTS_EDITOR,
    ORIGINAL_INSPECTOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashOverrideSettingsScreen(
    isRunning: Boolean = false,
    onRestartVpn: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var savedSettings by remember { mutableStateOf(OverrideSettingsRepository.load(context)) }
    var currentSettings by remember { mutableStateOf(savedSettings) }
    val originalSnapshot = remember { ConfigManager.getOriginalConfigSnapshot(context) }

    val hasChanges = currentSettings != savedSettings

    var activeSubPage by remember { mutableStateOf(OverrideSubPage.NONE) }
    var initialInspectorTab by remember { mutableIntStateOf(0) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showAllowLanDialog by remember { mutableStateOf(false) }
    var showIpv6Dialog by remember { mutableStateOf(false) }
    var showLogLevelDialog by remember { mutableStateOf(false) }
    var showDnsServersDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val effectiveSettings = currentSettings.copy(enableOverride = true)
                val result = ConfigManager.exportOverriddenConfigToUri(context, uri, effectiveSettings)
                result.onSuccess { byteCount ->
                    val sizeKb = String.format("%.1f KB", byteCount / 1024f)
                    Toast.makeText(context, "覆写后配置文件已导出 ($sizeKb)", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "导出失败: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun triggerExportConfig() {
        val sanitizedBase = originalSnapshot.profileName
            .replace(Regex("""[\\/:*?"<>|\s()]"""), "_")
            .trim('_')
            .ifBlank { "mihotier_config" }
        exportLauncher.launch("${sanitizedBase}_overridden.yaml")
    }

    // Sub-page 1: Full-screen Custom Rules Editor
    if (activeSubPage == OverrideSubPage.RULES_EDITOR) {
        OverrideTextEditorScreen(
            title = "前置自定义规则 (Custom Rules)",
            badgeText = "RULES",
            originalHint = "原配置共有 ${originalSnapshot.rulesList.size} 条规则，自定义规则将优先插入到最顶部",
            initialText = currentSettings.customRules,
            placeholder = "# 每行一条 Clash 规则，将优先插入到 rules 最顶部\n# 示例：\n# DOMAIN-SUFFIX,google.com,PROXY\n# IP-CIDR,192.168.1.0/24,DIRECT,no-resolve",
            quickTemplates = listOf(
                "DOMAIN-SUFFIX,example.com,PROXY",
                "DOMAIN-KEYWORD,google,PROXY",
                "IP-CIDR,192.168.0.0/16,DIRECT,no-resolve",
                "PROCESS-NAME,com.android.chrome,PROXY",
                "DOMAIN,ad.example.com,REJECT"
            ),
            onViewOriginalClick = {
                initialInspectorTab = 1
                activeSubPage = OverrideSubPage.ORIGINAL_INSPECTOR
            },
            onSave = { updatedText ->
                currentSettings = currentSettings.copy(
                    customRules = updatedText,
                    enableCustomRules = true
                )
                activeSubPage = OverrideSubPage.NONE
            },
            onNavigateBack = { activeSubPage = OverrideSubPage.NONE }
        )
        return
    }

    // Sub-page 2: Full-screen Custom Hosts Editor
    if (activeSubPage == OverrideSubPage.HOSTS_EDITOR) {
        OverrideTextEditorScreen(
            title = "自定义 Hosts (Custom Hosts)",
            badgeText = "HOSTS",
            originalHint = "原配置共有 ${originalSnapshot.hostsList.size} 条静态 Hosts 映射",
            initialText = currentSettings.customHosts,
            placeholder = "# 每行一条域名映射 (格式: 域名: IP)\n# 示例：\n# localhost: 127.0.0.1\n# nas.local: 192.168.1.100",
            quickTemplates = listOf(
                "localhost: 127.0.0.1",
                "router.local: 192.168.1.1",
                "nas.local: 192.168.1.10"
            ),
            onViewOriginalClick = {
                initialInspectorTab = 2
                activeSubPage = OverrideSubPage.ORIGINAL_INSPECTOR
            },
            onSave = { updatedText ->
                currentSettings = currentSettings.copy(
                    customHosts = updatedText,
                    enableCustomHosts = true
                )
                activeSubPage = OverrideSubPage.NONE
            },
            onNavigateBack = { activeSubPage = OverrideSubPage.NONE }
        )
        return
    }

    // Sub-page 3: Full-screen Original Config Inspector & Diff Viewer
    if (activeSubPage == OverrideSubPage.ORIGINAL_INSPECTOR) {
        OriginalConfigInspectorScreen(
            snapshot = originalSnapshot,
            currentSettings = currentSettings,
            initialTab = initialInspectorTab,
            onExportClick = { triggerExportConfig() },
            onNavigateBack = { activeSubPage = OverrideSubPage.NONE }
        )
        return
    }

    fun doSave() {
        OverrideSettingsRepository.save(context, currentSettings)
        savedSettings = currentSettings
        if (isRunning) {
            onRestartVpn()
            Toast.makeText(context, "覆写设置已保存，正在重启代理生效", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "覆写设置已保存", Toast.LENGTH_SHORT).show()
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

    val activeRuleCount = remember(currentSettings.customRules) {
        currentSettings.customRules.lines()
            .map { it.trim() }
            .count { it.isNotEmpty() && !it.startsWith("#") }
    }

    val activeHostCount = remember(currentSettings.customHosts) {
        currentSettings.customHosts.lines()
            .map { it.trim() }
            .count { it.isNotEmpty() && !it.startsWith("#") && it.contains(":") }
    }

    val origPort = originalSnapshot.mixedPort ?: "未定义(7890)"
    val origMode = originalSnapshot.mode ?: "rule"
    val origLan = originalSnapshot.allowLan ?: "false"
    val origIpv6 = originalSnapshot.ipv6 ?: "false"
    val origLogLevel = originalSnapshot.logLevel ?: "info"
    val origDnsSummary = if (originalSnapshot.dnsNameservers.isNotEmpty()) {
        originalSnapshot.dnsNameservers.joinToString(", ")
    } else {
        "原配置未指定"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "覆写设置 (Override)",
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
                    IconButton(onClick = { triggerExportConfig() }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Overridden Config",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
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
            // 1. Master Override Switch + Active Profile Original Config Inspector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OverrideCardSwitchItem(
                        icon = Icons.Default.Tune,
                        iconBg = Color(0xFFFED7AA),
                        iconTint = Color(0xFF9A3412),
                        title = "启用全局覆写 (Enable Override)",
                        subtitle = "在加载订阅或本地配置文件时自动应用以下覆写项",
                        checked = currentSettings.enableOverride,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(enableOverride = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    // Active Profile Original Settings & Export Entry Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                initialInspectorTab = 0
                                activeSubPage = OverrideSubPage.ORIGINAL_INSPECTOR
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFBAE6FD)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Original Config",
                                tint = Color(0xFF0369A1),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "当前基准配置: ${originalSnapshot.profileName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "原含 ${originalSnapshot.rulesList.size} 条规则 · 端口 $origPort · 模式 $origMode",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AppTheme.colors.statusInfoBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = AppTheme.colors.statusInfoText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "原始设置",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.statusInfoText
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Rules & Hosts Override
            Text(
                text = "自定义规则与 Hosts (Rules & Hosts)",
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
                    OverrideCardSwitchItem(
                        icon = Icons.AutoMirrored.Filled.Rule,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "启用前置自定义规则",
                        originalBadge = "原: ${originalSnapshot.rulesList.size} 条",
                        isOverridden = currentSettings.enableCustomRules && activeRuleCount > 0,
                        subtitle = "将自定义分流规则优先插入到配置文件 rules 顶部",
                        checked = currentSettings.enableCustomRules,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(enableCustomRules = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    OverrideCardNavigationItem(
                        icon = Icons.Default.EditNote,
                        iconBg = Color(0xFFE0E7FF),
                        iconTint = Color(0xFF3730A3),
                        title = "编辑自定义规则列表",
                        subtitle = if (activeRuleCount > 0) "已配置 $activeRuleCount 条前置规则 (点击编辑或查看原规则)" else "点击编写 DOMAIN / IP-CIDR / PROCESS-NAME 等规则",
                        onClick = { activeSubPage = OverrideSubPage.RULES_EDITOR }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    OverrideCardSwitchItem(
                        icon = Icons.Default.Storage,
                        iconBg = Color(0xFFA7F3D0),
                        iconTint = Color(0xFF065F46),
                        title = "启用自定义 Hosts 映射",
                        originalBadge = "原: ${originalSnapshot.hostsList.size} 条",
                        isOverridden = currentSettings.enableCustomHosts && activeHostCount > 0,
                        subtitle = "为特定域名指定静态 IP 解析结果",
                        checked = currentSettings.enableCustomHosts,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(enableCustomHosts = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    OverrideCardNavigationItem(
                        icon = Icons.Default.EditNote,
                        iconBg = Color(0xFFCCFBF1),
                        iconTint = Color(0xFF0F766E),
                        title = "编辑自定义 Hosts 映射",
                        subtitle = if (activeHostCount > 0) "已配置 $activeHostCount 条域名静态映射" else "点击配置域名与 IP 映射关系",
                        onClick = { activeSubPage = OverrideSubPage.HOSTS_EDITOR }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: General & Port Override
            Text(
                text = "基础参数覆写 (General & Ports)",
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
                    OverrideCardNavigationItem(
                        icon = Icons.Default.Router,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "混合代理端口 (Mixed Port)",
                        originalBadge = "原: $origPort",
                        isOverridden = currentSettings.mixedPort.isNotBlank(),
                        subtitle = if (currentSettings.mixedPort.isBlank()) "不覆写 · 沿用原配置端口 ($origPort)" else "已覆写为: ${currentSettings.mixedPort} (原配置: $origPort)",
                        onClick = { showPortDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    OverrideCardNavigationItem(
                        icon = Icons.Default.Shuffle,
                        iconBg = Color(0xFFA7F3D0),
                        iconTint = Color(0xFF065F46),
                        title = "出站运行模式 (Mode)",
                        originalBadge = "原: $origMode",
                        isOverridden = currentSettings.mode != "Keep",
                        subtitle = formatModeOverrideLabel(currentSettings.mode, origMode),
                        onClick = { showModeDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    OverrideCardNavigationItem(
                        icon = Icons.Default.Lan,
                        iconBg = Color(0xFFFED7AA),
                        iconTint = Color(0xFF9A3412),
                        title = "允许局域网连接 (Allow LAN)",
                        originalBadge = "原: $origLan",
                        isOverridden = currentSettings.allowLan != "Keep",
                        subtitle = formatTriStateLabel(currentSettings.allowLan, origLan),
                        onClick = { showAllowLanDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    OverrideCardNavigationItem(
                        icon = Icons.Default.Language,
                        iconBg = Color(0xFFCCFBF1),
                        iconTint = Color(0xFF0F766E),
                        title = "IPv6 解析与转发 (IPv6)",
                        originalBadge = "原: $origIpv6",
                        isOverridden = currentSettings.ipv6 != "Keep",
                        subtitle = formatTriStateLabel(currentSettings.ipv6, origIpv6),
                        onClick = { showIpv6Dialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    OverrideCardNavigationItem(
                        icon = Icons.Default.BugReport,
                        iconBg = Color(0xFFFBCFE8),
                        iconTint = Color(0xFF9D174D),
                        title = "默认日志级别 (Log Level)",
                        originalBadge = "原: $origLogLevel",
                        isOverridden = currentSettings.logLevel != "Keep",
                        subtitle = formatLogLevelLabel(currentSettings.logLevel, origLogLevel),
                        onClick = { showLogLevelDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: DNS Override
            Text(
                text = "DNS 覆写配置 (DNS Override)",
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
                    OverrideCardSwitchItem(
                        icon = Icons.Default.Dns,
                        iconBg = Color(0xFFE0E7FF),
                        iconTint = Color(0xFF3730A3),
                        title = "接管并覆写 DNS 配置",
                        originalBadge = "原: ${originalSnapshot.dnsEnhancedMode ?: "标准"}",
                        isOverridden = currentSettings.overrideDns,
                        subtitle = "强制开启 Fake-IP (198.18.0.1/16) 与内置 DNS 监听以配合 VPN 分流",
                        checked = currentSettings.overrideDns,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(overrideDns = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    OverrideCardNavigationItem(
                        icon = Icons.Default.Dns,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "上游 DNS 服务器 (Nameserver)",
                        originalBadge = "原: ${originalSnapshot.dnsNameservers.size} 个",
                        isOverridden = currentSettings.overrideDns,
                        subtitle = "覆写: ${currentSettings.nameservers.ifBlank { "114.114.114.114, 8.8.8.8" }}\n原配置: $origDnsSummary",
                        onClick = { showDnsServersDialog = true }
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
                    text = if (hasChanges) "保存并应用覆写" else "保存覆写设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Mixed Port Dialog
        if (showPortDialog) {
            var tempPort by remember { mutableStateOf(currentSettings.mixedPort) }
            AlertDialog(
                onDismissRequest = { showPortDialog = false },
                containerColor = AppTheme.colors.cardBackground,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "混合代理端口 (Mixed Port)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "原配置文件端口值：$origPort\n留空表示不覆写，直接沿用原配置端口：",
                            fontSize = 13.sp,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = tempPort,
                            onValueChange = { input ->
                                tempPort = input.filter { it.isDigit() }.take(5)
                            },
                            placeholder = {
                                Text("留空沿用原配置 ($origPort)", color = AppTheme.colors.textMuted, fontSize = 14.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = appTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        currentSettings = currentSettings.copy(mixedPort = tempPort.trim())
                        showPortDialog = false
                    }) {
                        Text("确定", color = AppTheme.colors.brandPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPortDialog = false }) {
                        Text("取消", color = AppTheme.colors.textSecondary)
                    }
                }
            )
        }

        // DNS Servers Dialog
        if (showDnsServersDialog) {
            var tempDns by remember { mutableStateOf(currentSettings.nameservers) }
            AlertDialog(
                onDismissRequest = { showDnsServersDialog = false },
                containerColor = AppTheme.colors.cardBackground,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "上游 DNS 服务器 (Nameserver)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "原配置 DNS：$origDnsSummary\n多个覆写 DNS 服务器请使用英文逗号分隔：",
                            fontSize = 13.sp,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = tempDns,
                            onValueChange = { tempDns = it },
                            placeholder = {
                                Text("114.114.114.114, 223.5.5.5, 8.8.8.8", color = AppTheme.colors.textMuted, fontSize = 14.sp)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = appTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        currentSettings = currentSettings.copy(nameservers = tempDns.trim())
                        showDnsServersDialog = false
                    }) {
                        Text("确定", color = AppTheme.colors.brandPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDnsServersDialog = false }) {
                        Text("取消", color = AppTheme.colors.textSecondary)
                    }
                }
            )
        }

        // Mode Override Dialog
        if (showModeDialog) {
            OverrideRadioDialog(
                title = "出站运行模式 (Mode)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origMode)",
                    "rule" to "规则分流 (rule)",
                    "global" to "全局代理 (global)",
                    "direct" to "直连模式 (direct)"
                ),
                selectedValue = currentSettings.mode,
                onSelect = {
                    currentSettings = currentSettings.copy(mode = it)
                    showModeDialog = false
                },
                onDismiss = { showModeDialog = false }
            )
        }

        // Allow LAN Dialog
        if (showAllowLanDialog) {
            OverrideRadioDialog(
                title = "允许局域网连接 (Allow LAN)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origLan)",
                    "true" to "开启 (true)",
                    "false" to "关闭 (false)"
                ),
                selectedValue = currentSettings.allowLan,
                onSelect = {
                    currentSettings = currentSettings.copy(allowLan = it)
                    showAllowLanDialog = false
                },
                onDismiss = { showAllowLanDialog = false }
            )
        }

        // IPv6 Dialog
        if (showIpv6Dialog) {
            OverrideRadioDialog(
                title = "IPv6 解析与转发 (IPv6)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origIpv6)",
                    "true" to "开启 (true)",
                    "false" to "关闭 (false)"
                ),
                selectedValue = currentSettings.ipv6,
                onSelect = {
                    currentSettings = currentSettings.copy(ipv6 = it)
                    showIpv6Dialog = false
                },
                onDismiss = { showIpv6Dialog = false }
            )
        }

        // Log Level Dialog
        if (showLogLevelDialog) {
            OverrideRadioDialog(
                title = "默认日志级别 (Log Level)",
                options = listOf(
                    "Keep" to "不覆写 (沿用原配置: $origLogLevel)",
                    "info" to "INFO (常规信息)",
                    "warning" to "WARNING (警告信息)",
                    "error" to "ERROR (仅错误信息)",
                    "debug" to "DEBUG (调试详情)",
                    "silent" to "SILENT (静默不输出)"
                ),
                selectedValue = currentSettings.logLevel,
                onSelect = {
                    currentSettings = currentSettings.copy(logLevel = it)
                    showLogLevelDialog = false
                },
                onDismiss = { showLogLevelDialog = false }
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
                        text = "恢复默认覆写设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "确定要将所有覆写参数、自定义规则与 Hosts 恢复为初始默认状态吗？",
                        fontSize = 14.sp,
                        color = AppTheme.colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        currentSettings = OverrideSettings()
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
                        text = "当前覆写设置有未保存的更改，是否在离开前保存？",
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

private data class ConfigDiffRow(
    val label: String,
    val keyName: String,
    val originalValue: String,
    val effectiveValue: String,
    val isOverridden: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OriginalConfigInspectorScreen(
    snapshot: OriginalConfigSnapshot,
    currentSettings: OverrideSettings,
    initialTab: Int = 0,
    onExportClick: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var showOverriddenYaml by remember { mutableStateOf(false) }

    val overriddenYaml = remember(snapshot.rawYaml, currentSettings) {
        ConfigManager.generateOverriddenConfig(
            context,
            currentSettings.copy(enableOverride = true)
        )
    }

    BackHandler(onBack = onNavigateBack)

    val tabs = listOf(
        "参数对比",
        "原始规则 (${snapshot.rulesList.size})",
        "DNS & Hosts",
        "YAML 源码"
    )

    val origPort = snapshot.mixedPort ?: "7890 (默认)"
    val origMode = snapshot.mode ?: "rule (默认)"
    val origLan = snapshot.allowLan ?: "false (默认)"
    val origIpv6 = snapshot.ipv6 ?: "false (默认)"
    val origLog = snapshot.logLevel ?: "info (默认)"
    val origDns = if (snapshot.dnsNameservers.isNotEmpty()) {
        snapshot.dnsNameservers.joinToString(", ")
    } else {
        "未定义"
    }

    val customRuleCount = currentSettings.customRules.lines()
        .map { it.trim() }
        .count { it.isNotEmpty() && !it.startsWith("#") }
    val customHostCount = currentSettings.customHosts.lines()
        .map { it.trim() }
        .count { it.isNotEmpty() && !it.startsWith("#") && it.contains(":") }

    val diffRows = remember(snapshot, currentSettings) {
        val enabled = currentSettings.enableOverride
        listOf(
            ConfigDiffRow(
                label = "混合代理端口",
                keyName = "mixed-port",
                originalValue = origPort,
                effectiveValue = if (enabled && currentSettings.mixedPort.isNotBlank()) currentSettings.mixedPort else origPort,
                isOverridden = enabled && currentSettings.mixedPort.isNotBlank()
            ),
            ConfigDiffRow(
                label = "出站运行模式",
                keyName = "mode",
                originalValue = origMode,
                effectiveValue = if (enabled && currentSettings.mode != "Keep") currentSettings.mode else origMode,
                isOverridden = enabled && currentSettings.mode != "Keep"
            ),
            ConfigDiffRow(
                label = "允许局域网连接",
                keyName = "allow-lan",
                originalValue = origLan,
                effectiveValue = if (enabled && currentSettings.allowLan != "Keep") currentSettings.allowLan else origLan,
                isOverridden = enabled && currentSettings.allowLan != "Keep"
            ),
            ConfigDiffRow(
                label = "IPv6 路由与解析",
                keyName = "ipv6",
                originalValue = origIpv6,
                effectiveValue = if (enabled && currentSettings.ipv6 != "Keep") currentSettings.ipv6 else origIpv6,
                isOverridden = enabled && currentSettings.ipv6 != "Keep"
            ),
            ConfigDiffRow(
                label = "默认日志级别",
                keyName = "log-level",
                originalValue = origLog,
                effectiveValue = if (enabled && currentSettings.logLevel != "Keep") currentSettings.logLevel else origLog,
                isOverridden = enabled && currentSettings.logLevel != "Keep"
            ),
            ConfigDiffRow(
                label = "分流规则 (Rules)",
                keyName = "rules",
                originalValue = "${snapshot.rulesList.size} 条原始规则",
                effectiveValue = if (enabled && currentSettings.enableCustomRules && customRuleCount > 0) {
                    "置顶 +$customRuleCount 条自定义规则 (共 ${snapshot.rulesList.size + customRuleCount} 条)"
                } else {
                    "${snapshot.rulesList.size} 条原始规则"
                },
                isOverridden = enabled && currentSettings.enableCustomRules && customRuleCount > 0
            ),
            ConfigDiffRow(
                label = "静态域名映射 (Hosts)",
                keyName = "hosts",
                originalValue = "${snapshot.hostsList.size} 条原始映射",
                effectiveValue = if (enabled && currentSettings.enableCustomHosts && customHostCount > 0) {
                    "注入 $customHostCount 条自定义 Hosts"
                } else {
                    "${snapshot.hostsList.size} 条原始映射"
                },
                isOverridden = enabled && currentSettings.enableCustomHosts && customHostCount > 0
            ),
            ConfigDiffRow(
                label = "上游 DNS 服务器",
                keyName = "dns.nameserver",
                originalValue = origDns,
                effectiveValue = if (enabled && currentSettings.overrideDns) {
                    "Fake-IP · ${currentSettings.nameservers}"
                } else {
                    origDns
                },
                isOverridden = enabled && currentSettings.overrideDns
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "原始配置与覆写对比",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "当前配置文件: ${snapshot.profileName}",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AppTheme.colors.statusSuccessBg,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { onExportClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export",
                                tint = AppTheme.colors.statusSuccessText,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "导出覆写配置",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.statusSuccessText
                            )
                        }
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
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Segmented Pill Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(AppTheme.colors.surfaceVariant)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tabTitle ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) AppTheme.colors.tabSelectedBg else Color.Transparent)
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabTitle,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AppTheme.colors.tabSelectedText else AppTheme.colors.tabUnselectedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // Tab 0: Parameter Comparison Cards
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        itemsIndexed(diffRows) { _, row ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                                border = BorderStroke(
                                    width = if (row.isOverridden) 1.5.dp else 1.dp,
                                    color = if (row.isOverridden) AppTheme.colors.brandPrimary else AppTheme.colors.cardBorder
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = row.label,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AppTheme.colors.textPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = row.keyName,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = AppTheme.colors.textSecondary
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (row.isOverridden) AppTheme.colors.statusInfoBg else AppTheme.colors.surfaceSubtle
                                        ) {
                                            Text(
                                                text = if (row.isOverridden) "已覆写" else "沿用原配置",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (row.isOverridden) AppTheme.colors.statusInfoText else AppTheme.colors.textSecondary,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Left box: Original Value
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(AppTheme.colors.surfaceSubtle)
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "配置文件原始值",
                                                fontSize = 11.sp,
                                                color = AppTheme.colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = row.originalValue,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium,
                                                color = AppTheme.colors.textPrimary
                                            )
                                        }

                                        // Right box: Effective Value after override
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (row.isOverridden) AppTheme.colors.statusInfoBg.copy(alpha = 0.5f) else AppTheme.colors.surfaceSubtle
                                                )
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "最终生效值",
                                                fontSize = 11.sp,
                                                color = if (row.isOverridden) AppTheme.colors.statusInfoText else AppTheme.colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = row.effectiveValue,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (row.isOverridden) AppTheme.colors.statusInfoText else AppTheme.colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Tab 1: Original Rules List with Search Filter
                    val filteredRules = remember(snapshot.rulesList, searchQuery) {
                        if (searchQuery.isBlank()) {
                            snapshot.rulesList
                        } else {
                            val q = searchQuery.trim()
                            snapshot.rulesList.filter { it.contains(q, ignoreCase = true) }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(19.dp),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            color = AppTheme.colors.cardBackground
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "搜索原配置中的规则 (如 google / DIRECT / PROXY)...",
                                            fontSize = 13.sp,
                                            color = AppTheme.colors.textMuted
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 13.sp, color = AppTheme.colors.textPrimary),
                                        cursorBrush = SolidColor(AppTheme.colors.brandPrimary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = AppTheme.colors.textSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            if (filteredRules.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "未找到匹配的原始规则",
                                        fontSize = 14.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    itemsIndexed(filteredRules) { idx, rule ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "#${idx + 1}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = AppTheme.colors.textMuted,
                                                modifier = Modifier.width(44.dp)
                                            )
                                            Text(
                                                text = rule,
                                                fontSize = 12.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = AppTheme.colors.textPrimary
                                            )
                                        }
                                        HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Tab 2: Original DNS & Hosts YAML Sections
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "原始 DNS 配置段落 (dns:)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AppTheme.colors.statusInfoBg
                                    ) {
                                        Text(
                                            text = "只读",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppTheme.colors.statusInfoText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppTheme.colors.surfaceSubtle)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = snapshot.dnsSectionYaml.ifBlank { "# 原配置文件中未定义 dns: 段落" },
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp,
                                        color = AppTheme.colors.textPrimary
                                    )
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "原始 Hosts 配置段落 (hosts:)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AppTheme.colors.statusInfoBg
                                    ) {
                                        Text(
                                            text = "只读",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppTheme.colors.statusInfoText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppTheme.colors.surfaceSubtle)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = snapshot.hostsSectionYaml.ifBlank { "# 原配置文件中未定义 hosts: 段落" },
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp,
                                        color = AppTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // Tab 3: Full Raw YAML / Overridden YAML Read-only Viewer
                    val activeYamlText = if (showOverriddenYaml) overriddenYaml else snapshot.rawYaml
                    val rawLines = remember(activeYamlText, searchQuery) {
                        val all = activeYamlText.lines()
                        if (searchQuery.isBlank()) {
                            all.mapIndexed { idx, line -> (idx + 1) to line }
                        } else {
                            val q = searchQuery.trim()
                            all.mapIndexedNotNull { idx, line ->
                                if (line.contains(q, ignoreCase = true)) (idx + 1) to line else null
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(19.dp),
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                color = AppTheme.colors.cardBackground
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = if (showOverriddenYaml) "检索覆写后 YAML 内容..." else "检索原始 YAML 内容...",
                                                fontSize = 13.sp,
                                                color = AppTheme.colors.textMuted
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = TextStyle(fontSize = 13.sp, color = AppTheme.colors.textPrimary),
                                            cursorBrush = SolidColor(AppTheme.colors.brandPrimary),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = AppTheme.colors.textSecondary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { searchQuery = "" }
                                        )
                                    }
                                }
                            }

                            // Pill switcher between Original YAML and Overridden YAML
                            Row(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(AppTheme.colors.surfaceVariant)
                                    .padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (!showOverriddenYaml) AppTheme.colors.tabSelectedBg else Color.Transparent)
                                        .clickable { showOverriddenYaml = false }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "原始",
                                        fontSize = 12.sp,
                                        fontWeight = if (!showOverriddenYaml) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!showOverriddenYaml) AppTheme.colors.tabSelectedText else AppTheme.colors.tabUnselectedText
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (showOverriddenYaml) AppTheme.colors.statusSuccessBg else Color.Transparent)
                                        .clickable { showOverriddenYaml = true }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "覆写后",
                                        fontSize = 12.sp,
                                        fontWeight = if (showOverriddenYaml) FontWeight.Bold else FontWeight.Medium,
                                        color = if (showOverriddenYaml) AppTheme.colors.statusSuccessText else AppTheme.colors.tabUnselectedText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                itemsIndexed(rawLines) { _, (lineNum, lineContent) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = lineNum.toString().padStart(4, ' '),
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = AppTheme.colors.textMuted,
                                            modifier = Modifier.width(42.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = lineContent,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = AppTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverrideTextEditorScreen(
    title: String,
    badgeText: String,
    originalHint: String,
    initialText: String,
    placeholder: String,
    quickTemplates: List<String>,
    onViewOriginalClick: () -> Unit,
    onSave: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    BackHandler(onBack = onNavigateBack)

    val validLineCount = remember(text) {
        text.lines().map { it.trim() }.count { it.isNotEmpty() && !it.startsWith("#") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    // Header info + View Original Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "快捷插入模板",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppTheme.colors.statusInfoBg
                            ) {
                                Text(
                                    text = "$badgeText · $validLineCount 条",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.statusInfoText,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AppTheme.colors.statusInfoBg,
                            modifier = Modifier.clickable { onViewOriginalClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = AppTheme.colors.statusInfoText,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "查看原配置",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.statusInfoText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = originalHint,
                        fontSize = 12.sp,
                        color = AppTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick template chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickTemplates.forEach { tpl ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AppTheme.colors.surfaceSubtle,
                                border = BorderStroke(1.dp, AppTheme.colors.innerBorder),
                                modifier = Modifier.clickable {
                                    text = if (text.isBlank() || text.endsWith("\n")) {
                                        "$text$tpl\n"
                                    } else {
                                        "$text\n$tpl\n"
                                    }
                                }
                            ) {
                                Text(
                                    text = "+ $tpl",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppTheme.colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Full-height Monospace Editor
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = {
                            Text(
                                text = placeholder,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = AppTheme.colors.textMuted
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = AppTheme.colors.textPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = appTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onSave(text) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.brandPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "完成编辑",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverrideRadioDialog(
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
                            fontSize = 15.sp,
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

private fun formatModeOverrideLabel(mode: String, origMode: String): String {
    return when (mode) {
        "rule" -> "已覆写为: 规则分流 (rule) · 原配置: $origMode"
        "global" -> "已覆写为: 全局代理 (global) · 原配置: $origMode"
        "direct" -> "已覆写为: 直连模式 (direct) · 原配置: $origMode"
        else -> "不覆写 · 沿用原配置 ($origMode)"
    }
}

private fun formatTriStateLabel(value: String, origValue: String): String {
    return when (value) {
        "true" -> "已覆写为: 开启 (true) · 原配置: $origValue"
        "false" -> "已覆写为: 关闭 (false) · 原配置: $origValue"
        else -> "不覆写 · 沿用原配置 ($origValue)"
    }
}

private fun formatLogLevelLabel(level: String, origLevel: String): String {
    return when (level) {
        "info", "warning", "error", "debug", "silent" ->
            "已覆写为: ${level.uppercase()} · 原配置: $origLevel"
        else -> "不覆写 · 沿用原配置 ($origLevel)"
    }
}

@Composable
private fun OverrideCardSwitchItem(
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
private fun OverrideCardNavigationItem(
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
