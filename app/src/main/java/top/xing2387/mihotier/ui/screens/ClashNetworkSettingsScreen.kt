package top.xing2387.mihotier.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { doSave() }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (hasChanges) Color(0xFF1D68BD) else Color(0xFF0F172A)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEEF3F8)
                )
            )
        },
        containerColor = Color(0xFFEEF3F8)
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                NetworkCardSwitchItem(
                    icon = Icons.Default.Public,
                    iconBg = Color(0xFFA7F3D0),
                    iconTint = Color(0xFF065F46),
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
                color = Color(0xFF475569),
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            // 2. VpnService Options Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NetworkCardSwitchItem(
                        icon = Icons.Default.Lan,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "绕过私有网络 (Bypass Private Network)",
                        subtitle = "直连局域网与私有网段地址，不经过代理",
                        checked = currentSettings.bypassPrivateNetwork,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(bypassPrivateNetwork = it)
                        }
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    NetworkCardSwitchItem(
                        icon = Icons.Default.Dns,
                        iconBg = Color(0xFFE0E7FF),
                        iconTint = Color(0xFF3730A3),
                        title = "DNS 劫持 (DNS Hijacking)",
                        subtitle = "拦截并处理所有系统 DNS 解析数据包",
                        checked = currentSettings.dnsHijacking,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(dnsHijacking = it)
                        }
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    NetworkCardSwitchItem(
                        icon = Icons.AutoMirrored.Filled.CallSplit,
                        iconBg = Color(0xFFFED7AA),
                        iconTint = Color(0xFF9A3412),
                        title = "允许应用绕过 (Allow Bypass)",
                        subtitle = "允许应用声明绕过当前 VPN 连接直接访问网络",
                        checked = currentSettings.allowBypass,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(allowBypass = it)
                        }
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    NetworkCardSwitchItem(
                        icon = Icons.Default.Language,
                        iconBg = Color(0xFFCCFBF1),
                        iconTint = Color(0xFF0F766E),
                        title = "允许 IPv6 (Allow IPv6)",
                        subtitle = "允许通过 VpnService 转发 IPv6 网络流量",
                        checked = currentSettings.allowIpv6,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(allowIpv6 = it)
                        }
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    NetworkCardSwitchItem(
                        icon = Icons.Default.Hub,
                        iconBg = Color(0xFFFBCFE8),
                        iconTint = Color(0xFF9D174D),
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
                color = Color(0xFF475569),
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            // 3. Stack & Access Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NetworkCardNavigationItem(
                        icon = Icons.Default.Layers,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "协议栈模式 (Stack Mode)",
                        subtitle = formatStackModeLabel(currentSettings.stackMode),
                        onClick = { showStackModeDialog = true }
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    NetworkCardNavigationItem(
                        icon = Icons.Default.Shield,
                        iconBg = Color(0xFFA7F3D0),
                        iconTint = Color(0xFF065F46),
                        title = "分应用控制模式 (Access Control Mode)",
                        subtitle = formatAccessControlLabel(currentSettings.accessControlMode),
                        onClick = { showAccessControlDialog = true }
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    val pkgCount = currentSettings.accessControlPackages.size
                    NetworkCardNavigationItem(
                        icon = Icons.Default.Apps,
                        iconBg = Color(0xFFFED7AA),
                        iconTint = Color(0xFF9A3412),
                        title = "分应用代理列表 (Access Control Packages)",
                        subtitle = if (pkgCount == 0) "配置允许或绕过代理的应用程序" else "已选择 $pkgCount 个应用",
                        onClick = onNavigateToAppSelection
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
                    containerColor = Color(0xFF104179),
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
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "未保存的修改",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                },
                text = {
                    Text(
                        text = "当前网络设置有未保存的更改，是否在离开前保存？",
                        fontSize = 14.sp,
                        color = Color(0xFF475569)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        doSave()
                        onNavigateBack()
                    }) {
                        Text("保存并退出", color = Color(0xFF104179), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showUnsavedDialog = false }) {
                            Text("取消", color = Color(0xFF64748B))
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
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "协议栈模式 (Stack Mode)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
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
                                        selectedColor = Color(0xFF1D68BD),
                                        unselectedColor = Color(0xFF64748B)
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formatStackModeLabel(mode),
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A),
                                    fontWeight = if (currentSettings.stackMode == mode) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showStackModeDialog = false }) {
                        Text("取消", color = Color(0xFF64748B))
                    }
                }
            )
        }

        // Access Control Mode dialog
        if (showAccessControlDialog) {
            AlertDialog(
                onDismissRequest = { showAccessControlDialog = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "分应用控制模式",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
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
                                        selectedColor = Color(0xFF1D68BD),
                                        unselectedColor = Color(0xFF64748B)
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formatAccessControlLabel(mode),
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A),
                                    fontWeight = if (currentSettings.accessControlMode == mode) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAccessControlDialog = false }) {
                        Text("取消", color = Color(0xFF64748B))
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
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = Color(0xFF64748B),
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1D68BD),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1)
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
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = Color(0xFF64748B),
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Select",
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(14.dp)
        )
    }
}
