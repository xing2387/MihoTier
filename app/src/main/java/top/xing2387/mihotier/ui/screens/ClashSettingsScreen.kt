package top.xing2387.mihotier.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
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

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import top.xing2387.mihotier.core.config.AppSettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNetwork: () -> Unit
) {
    val context = LocalContext.current
    var showSpeedNotification by remember {
        mutableStateOf(AppSettingsRepository.isShowSpeedNotification(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "未开启通知权限，通知栏将无法显示网速", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置 (Settings)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. 通知栏显示实时网速 (Switch)
                    SettingsSwitchItem(
                        icon = Icons.Default.NotificationsActive,
                        iconBg = Color(0xFFBAE6FD),
                        iconTint = Color(0xFF0369A1),
                        title = "通知栏显示实时网速",
                        subtitle = "在系统通知中展示当前上行与下行带宽",
                        checked = showSpeedNotification,
                        onCheckedChange = { checked ->
                            showSpeedNotification = checked
                            AppSettingsRepository.setShowSpeedNotification(context, checked)
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // 2. 网络设置 (Network)
                    SettingsNavigationItem(
                        icon = Icons.Default.Public,
                        iconBg = Color(0xFFA7F3D0),
                        iconTint = Color(0xFF065F46),
                        title = "网络设置 (Network)",
                        subtitle = "系统代理、DNS、Bypass LAN及IPv6",
                        onClick = onNavigateToNetwork
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // 3. 覆写设置 (Override)
                    SettingsNavigationItem(
                        icon = Icons.Default.Tune,
                        iconBg = Color(0xFFFED7AA),
                        iconTint = Color(0xFF9A3412),
                        title = "覆写设置 (Override)",
                        subtitle = "自定义规则、hosts 与 DNS 覆写配置",
                        onClick = {
                            Toast.makeText(context, "覆写设置暂未开放", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // 4. Meta 特性 (Meta Features)
                    SettingsNavigationItem(
                        icon = Icons.Default.AutoAwesome,
                        iconBg = Color(0xFFFBCFE8),
                        iconTint = Color(0xFF9D174D),
                        title = "Meta 特性 (Meta Features)",
                        subtitle = "内核高级功能、进程过滤及实验特性",
                        onClick = {
                            Toast.makeText(context, "Meta 特性暂未开放", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
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
private fun SettingsNavigationItem(
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
            contentDescription = "Navigate",
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(14.dp)
        )
    }
}
