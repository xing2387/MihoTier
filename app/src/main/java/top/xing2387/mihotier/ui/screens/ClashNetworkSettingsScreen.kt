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
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.xing2387.mihotier.core.config.NetworkSettingsRepository
import top.xing2387.mihotier.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashNetworkSettingsScreen(
    isRunning: Boolean = false,
    onRestartVpn: () -> Unit = {},
    onNavigateToAppSelection: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var savedSettings by remember { mutableStateOf(NetworkSettingsRepository.load(context)) }
    var currentSettings by remember { mutableStateOf(savedSettings) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val latest = NetworkSettingsRepository.load(context)
                if (latest.accessControlPackages != savedSettings.accessControlPackages) {
                    savedSettings = savedSettings.copy(accessControlPackages = latest.accessControlPackages)
                    currentSettings = currentSettings.copy(accessControlPackages = latest.accessControlPackages)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hasChanges = currentSettings != savedSettings

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showStackModeDialog by remember { mutableStateOf(false) }
    var showAccessControlDialog by remember { mutableStateOf(false) }

    fun doSave() {
        NetworkSettingsRepository.save(context, currentSettings)
        savedSettings = currentSettings
        if (isRunning) {
            onRestartVpn()
            Toast.makeText(context, "网络设置已保存，正在重启代理", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "网络设置已保存", Toast.LENGTH_SHORT).show()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "网络设置 (Network)",
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
                    IconButton(onClick = { doSave() }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (hasChanges) AppTheme.colors.brandPrimary else AppTheme.colors.textPrimary
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
            // 1. Primary VPN Routing Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                NetworkCardSwitchItem(
                    icon = Icons.Default.Public,
                    iconBg = AppTheme.colors.statusSuccessBg,
                    iconTint = AppTheme.colors.statusSuccessText,
                    title = "路由系统流量 (Route System Traffic)",
                    subtitle = "通过 VpnService 自动接管并路由全局系统流量",
                    checked = currentSettings.routeSystemTraffic,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(routeSystemTraffic = checked)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Title: VpnService Options
            Text(
                text = "VPN 服务选项 (VpnService Options)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            // 2. VpnService Options Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NetworkCardSwitchItem(
                        icon = Icons.Default.Lan,
                        iconBg = AppTheme.colors.statusInfoBg,
                        iconTint = AppTheme.colors.statusInfoText,
                        title = "绕过私有网络 (Bypass Private Network)",
                        subtitle = "直连局域网与私有网段地址，不经过代理",
                        checked = currentSettings.bypassPrivateNetwork,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(bypassPrivateNetwork = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    NetworkCardSwitchItem(
                        icon = Icons.Default.Dns,
                        iconBg = AppTheme.colors.statusInfoBg,
                        iconTint = AppTheme.colors.statusInfoText,
                        title = "DNS 劫持 (DNS Hijacking)",
                        subtitle = "拦截并处理所有系统 DNS 解析数据包",
                        checked = currentSettings.dnsHijacking,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(dnsHijacking = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    NetworkCardSwitchItem(
                        icon = Icons.AutoMirrored.Filled.CallSplit,
                        iconBg = AppTheme.colors.statusWarningBg,
                        iconTint = AppTheme.colors.statusWarningText,
                        title = "允许应用绕过 (Allow Bypass)",
                        subtitle = "允许应用声明绕过当前 VPN 连接直接访问网络",
                        checked = currentSettings.allowBypass,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(allowBypass = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    NetworkCardSwitchItem(
                        icon = Icons.Default.Language,
                        iconBg = AppTheme.colors.statusInfoBg,
                        iconTint = AppTheme.colors.statusInfoText,
                        title = "允许 IPv6 (Allow IPv6)",
                        subtitle = "允许通过 VpnService 转发 IPv6 网络流量",
                        checked = currentSettings.allowIpv6,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(allowIpv6 = it)
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    NetworkCardSwitchItem(
                        icon = Icons.Default.Hub,
                        iconBg = AppTheme.colors.statusPurpleBg,
                        iconTint = AppTheme.colors.statusPurpleText,
                        title = "系统代理 (System Proxy)",
                        subtitle = "将 HTTP 代理自动附加至 VpnService",
                        checked = currentSettings.systemProxy,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(systemProxy = it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Title: Stack & Access Control
            Text(
                text = "协议栈与分应用代理 (Stack & Access Control)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            // 3. Stack & Access Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NetworkCardNavigationItem(
                        icon = Icons.Default.Layers,
                        iconBg = AppTheme.colors.statusInfoBg,
                        iconTint = AppTheme.colors.statusInfoText,
                        title = "协议栈模式 (Stack Mode)",
                        subtitle = formatStackModeLabel(currentSettings.stackMode),
                        onClick = { showStackModeDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    NetworkCardNavigationItem(
                        icon = Icons.Default.Shield,
                        iconBg = AppTheme.colors.statusSuccessBg,
                        iconTint = AppTheme.colors.statusSuccessText,
                        title = "分应用控制模式 (Access Control Mode)",
                        subtitle = formatAccessControlLabel(currentSettings.accessControlMode),
                        onClick = { showAccessControlDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    val pkgCount = currentSettings.accessControlPackages.size
                    NetworkCardNavigationItem(
                        icon = Icons.Default.Apps,
                        iconBg = AppTheme.colors.statusWarningBg,
                        iconTint = AppTheme.colors.statusWarningText,
                        title = "分应用代理列表 (Access Control Packages)",
                        subtitle = if (pkgCount == 0) "配置允许或绕过代理的应用程序" else "已选择 $pkgCount 个应用",
                        onClick = {
                            if (currentSettings != savedSettings) {
                                NetworkSettingsRepository.save(context, currentSettings)
                                savedSettings = currentSettings
                            }
                            onNavigateToAppSelection()
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
                    text = if (hasChanges) "保存并应用设置" else "保存设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Unsaved changes dialog
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
                        text = "当前网络设置有未保存的更改，是否在离开前保存？",
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
                            Text("不保存", color = Color(0xFFDC2626))
                        }
                    }
                }
            )
        }

        // Stack Mode dialog
        if (showStackModeDialog) {
            AlertDialog(
                onDismissRequest = { showStackModeDialog = false },
                containerColor = AppTheme.colors.cardBackground,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "协议栈模式 (Stack Mode)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                text = {
                    Column {
                        listOf("System Stack", "gVisor", "Mixed").forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        currentSettings = currentSettings.copy(stackMode = mode)
                                        showStackModeDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentSettings.stackMode == mode),
                                    onClick = {
                                        currentSettings = currentSettings.copy(stackMode = mode)
                                        showStackModeDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AppTheme.colors.brandPrimary,
                                        unselectedColor = AppTheme.colors.textSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formatStackModeLabel(mode),
                                    fontSize = 15.sp,
                                    color = AppTheme.colors.textPrimary,
                                    fontWeight = if (currentSettings.stackMode == mode) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showStackModeDialog = false }) {
                        Text("取消", color = AppTheme.colors.textSecondary)
                    }
                }
            )
        }

        // Access Control Mode dialog
        if (showAccessControlDialog) {
            AlertDialog(
                onDismissRequest = { showAccessControlDialog = false },
                containerColor = AppTheme.colors.cardBackground,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "分应用控制模式",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                text = {
                    Column {
                        listOf("Allow all apps", "Disallow selected apps", "Allow selected apps").forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        currentSettings = currentSettings.copy(accessControlMode = mode)
                                        showAccessControlDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentSettings.accessControlMode == mode),
                                    onClick = {
                                        currentSettings = currentSettings.copy(accessControlMode = mode)
                                        showAccessControlDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AppTheme.colors.brandPrimary,
                                        unselectedColor = AppTheme.colors.textSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formatAccessControlLabel(mode),
                                    fontSize = 15.sp,
                                    color = AppTheme.colors.textPrimary,
                                    fontWeight = if (currentSettings.accessControlMode == mode) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAccessControlDialog = false }) {
                        Text("取消", color = AppTheme.colors.textSecondary)
                    }
                }
            )
        }
    }
}

private fun formatStackModeLabel(mode: String): String {
    return when (mode) {
        "System Stack" -> "System Stack (系统原生栈)"
        "gVisor" -> "gVisor (用户态协议栈)"
        "Mixed" -> "Mixed (混合协议栈)"
        else -> mode
    }
}

private fun formatAccessControlLabel(mode: String): String {
    return when (mode) {
        "Allow all apps" -> "允许所有应用 (Allow all apps)"
        "Disallow selected apps" -> "绕过选中的应用 (Disallow selected)"
        "Allow selected apps" -> "仅代理选中的应用 (Allow selected)"
        else -> mode
    }
}

@Composable
private fun NetworkCardSwitchItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
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
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )
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
                uncheckedTrackColor = AppTheme.colors.innerBorder
            )
        )
    }
}

@Composable
private fun NetworkCardNavigationItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
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
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )
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
            contentDescription = "Select",
            tint = AppTheme.colors.textMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
