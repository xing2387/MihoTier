package top.xing2387.mihotier.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import top.xing2387.mihotier.core.config.AppSettingsRepository
import top.xing2387.mihotier.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToOverride: () -> Unit = {},
    onNavigateToMetaFeatures: () -> Unit = {}
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
                        color = AppTheme.colors.textPrimary
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. 通知栏显示实时网速 (Switch)
                    SettingsSwitchItem(
                        icon = Icons.Default.NotificationsActive,
                        iconBg = AppTheme.colors.statusInfoBg,
                        iconTint = AppTheme.colors.statusInfoText,
                        title = "通知栏显示实时网速",
                        subtitle = "在系统通知中展示当前上行与下行带宽",
                        checked = showSpeedNotification,
                        onCheckedChange = { checked ->
                            showSpeedNotification = checked
                            AppSettingsRepository.setShowSpeedNotification(context, checked)
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    // 2. 网络设置 (Network)
                    SettingsNavigationItem(
                        icon = Icons.Default.Public,
                        iconBg = AppTheme.colors.statusSuccessBg,
                        iconTint = AppTheme.colors.statusSuccessText,
                        title = "网络设置 (Network)",
                        subtitle = "系统代理、DNS、Bypass LAN及IPv6",
                        onClick = onNavigateToNetwork
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    // 3. 覆写设置 (Override)
                    SettingsNavigationItem(
                        icon = Icons.Default.Tune,
                        iconBg = AppTheme.colors.statusWarningBg,
                        iconTint = AppTheme.colors.statusWarningText,
                        title = "覆写设置 (Override)",
                        subtitle = "自定义规则、hosts 与 DNS 覆写配置",
                        onClick = onNavigateToOverride
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)

                    // 4. Meta 特性 (Meta Features)
                    SettingsNavigationItem(
                        icon = Icons.Default.AutoAwesome,
                        iconBg = AppTheme.colors.statusPurpleBg,
                        iconTint = AppTheme.colors.statusPurpleText,
                        title = "Meta 特性 (Meta Features)",
                        subtitle = "内核高级功能、进程过滤及实验特性",
                        onClick = onNavigateToMetaFeatures
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
            contentDescription = "Navigate",
            tint = AppTheme.colors.textMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
