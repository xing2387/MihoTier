package top.xing2387.mihotier.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.xing2387.mihotier.core.config.EasyTierNetworkProfile
import top.xing2387.mihotier.core.config.EasyTierPortForward
import top.xing2387.mihotier.core.config.EasyTierRepository
import top.xing2387.mihotier.core.config.NetworkingMethod
import top.xing2387.mihotier.ui.theme.AppTheme
import top.xing2387.mihotier.ui.theme.appTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyTierScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogs: () -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        EasyTierRepository.init(context)
    }

    val profiles by EasyTierRepository.profiles.collectAsState()
    val selectedProfileId by EasyTierRepository.selectedProfileId.collectAsState()
    val runtimeState by EasyTierRepository.runtimeState.collectAsState()

    val currentProfile = remember(profiles, selectedProfileId) {
        profiles.find { it.id == selectedProfileId } ?: profiles.firstOrNull() ?: EasyTierNetworkProfile()
    }

    // Periodically refresh runtime status while running
    LaunchedEffect(runtimeState.isRunning) {
        while (isActive && runtimeState.isRunning) {
            EasyTierRepository.refreshRuntimeStatus()
            delay(2000L)
        }
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = 1. 网络配置, 1 = 2. 运行状态
    var showProfileDropdown by remember { mutableStateOf(false) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var showRenameProfileDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showTomlEditorSheet by remember { mutableStateOf(false) }
    var showDualCoreGuideDialog by remember { mutableStateOf(false) }
    var showAddPortForwardDialog by remember { mutableStateOf(false) }

    var showSecretPassword by remember { mutableStateOf(false) }
    var newPeerUrlInput by remember { mutableStateOf("") }
    var newProxyCidrInput by remember { mutableStateOf("") }
    var newListenerUrlInput by remember { mutableStateOf("") }

    var advancedExpanded by remember { mutableStateOf(false) }
    var portForwardExpanded by remember { mutableStateOf(false) }

    val runningProfile by EasyTierRepository.runningProfile.collectAsState()
    val isCurrentProfileRunning = runtimeState.isRunning && (runningProfile?.id == currentProfile.id || runtimeState.runningProfileId == currentProfile.id)
    val isConfigModified = isCurrentProfileRunning && runningProfile != null && runningProfile != currentProfile
    val isOtherProfileRunning = runtimeState.isRunning && runningProfile != null && runningProfile?.id != currentProfile.id

    fun doStartNetwork(switchToStatusTab: Boolean) {
        val res = EasyTierRepository.startNetwork(context, currentProfile)
        if (res.isSuccess) {
            Toast.makeText(context, "EasyTier 组网已启动", Toast.LENGTH_SHORT).show()
            if (switchToStatusTab) activeTab = 1
        } else {
            Toast.makeText(
                context,
                "启动失败: ${res.exceptionOrNull()?.message ?: "配置有误"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun doRestartNetwork(switchToStatusTab: Boolean) {
        val res = EasyTierRepository.restartNetwork(context, currentProfile)
        if (res.isSuccess) {
            Toast.makeText(context, "EasyTier 组网已更新并重启", Toast.LENGTH_SHORT).show()
            if (switchToStatusTab) activeTab = 1
        } else {
            Toast.makeText(
                context,
                "重启失败: ${res.exceptionOrNull()?.message ?: "配置有误"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    var pendingSwitchTabOnStart by remember { mutableStateOf(false) }
    var pendingActionIsRestart by remember { mutableStateOf(false) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (pendingActionIsRestart) {
                doRestartNetwork(pendingSwitchTabOnStart)
            } else {
                doStartNetwork(pendingSwitchTabOnStart)
            }
        } else {
            Toast.makeText(context, "未授予 VPN 权限，无法建立虚拟网卡", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchStartEasyTier(switchToStatusTab: Boolean) {
        pendingActionIsRestart = false
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            pendingSwitchTabOnStart = switchToStatusTab
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            doStartNetwork(switchToStatusTab)
        }
    }

    fun launchRestartEasyTier(switchToStatusTab: Boolean) {
        pendingActionIsRestart = true
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            pendingSwitchTabOnStart = switchToStatusTab
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            doRestartNetwork(switchToStatusTab)
        }
    }

    fun updateCurrent(transform: (EasyTierNetworkProfile) -> EasyTierNetworkProfile) {
        val updated = transform(currentProfile)
        EasyTierRepository.updateProfile(context, updated)
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EasyTier",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (runtimeState.isRunning) AppTheme.colors.statusSuccessBg else AppTheme.colors.statusInfoBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (runtimeState.isRunning) "RUNNING" else "CORE v2",
                                color = if (runtimeState.isRunning) AppTheme.colors.statusSuccessText else AppTheme.colors.statusInfoText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToLogs) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "事件日志",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                    IconButton(onClick = { showDualCoreGuideDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "双核桥接指南",
                            tint = AppTheme.colors.statusInfoText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.background
                )
            )
        },
        bottomBar = {
            if (activeTab == 0) {
                Surface(
                    color = AppTheme.colors.cardBackground,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showTomlEditorSheet = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.brandPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "编辑配置文件",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (currentProfile.useCustomToml) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = AppTheme.colors.statusWarningBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "自定义 TOML",
                                        color = AppTheme.colors.statusWarningText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                if (profiles.size <= 1) {
                                    Toast.makeText(context, "至少保留一个组网实例配置", Toast.LENGTH_SHORT).show()
                                } else {
                                    showDeleteConfirmDialog = true
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.statusDangerText)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "删除当前网络",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Network Profile Selector Row (Matches Screenshot: [+] [ Profile Selector (status) v ] [Edit])
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left '+' Button to add new network profile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppTheme.colors.brandPrimary,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showCreateProfileDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建网络",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Middle Network Instance Dropdown Selector
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.cardBackground,
                        border = BorderStroke(1.dp, AppTheme.colors.innerBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { showProfileDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = currentProfile.instanceName.ifBlank { "easytier" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (isCurrentProfileRunning) AppTheme.colors.statusSuccessBg else AppTheme.colors.surfaceVariant,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = if (isCurrentProfileRunning) "running" else "stopped",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCurrentProfileRunning) AppTheme.colors.statusSuccessText else AppTheme.colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "切换配置",
                                tint = AppTheme.colors.textSecondary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showProfileDropdown,
                        onDismissRequest = { showProfileDropdown = false },
                        modifier = Modifier.background(AppTheme.colors.cardBackground)
                    ) {
                        profiles.forEach { item ->
                            val isItemRunning = runtimeState.isRunning && runtimeState.runningProfileId == item.id
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = item.instanceName.ifBlank { "easytier" },
                                            fontWeight = if (item.id == currentProfile.id) FontWeight.Bold else FontWeight.Normal,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        Surface(
                                            color = if (isItemRunning) AppTheme.colors.statusSuccessBg else AppTheme.colors.surfaceVariant,
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Text(
                                                text = if (isItemRunning) "running" else "stopped",
                                                fontSize = 10.sp,
                                                color = if (isItemRunning) AppTheme.colors.statusSuccessText else AppTheme.colors.textSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    EasyTierRepository.selectProfile(context, item.id)
                                    showProfileDropdown = false
                                }
                            )
                        }
                    }
                }

                // Right Edit Name / Settings Button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppTheme.colors.cardBackground,
                    border = BorderStroke(1.dp, AppTheme.colors.innerBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showRenameProfileDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "实例设置",
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // 2. Step Segmented Switcher: [ 1. 网络配置 ] | [ 2. 运行状态 ]
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppTheme.colors.cardBackground,
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf("1. 网络配置 (Config)", "2. 运行状态 (Running)")
                    tabs.forEachIndexed { index, title ->
                        val selected = activeTab == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) AppTheme.colors.tabSelectedBg else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = index }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (index == 1 && runtimeState.isRunning) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AppTheme.colors.statusSuccessText)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) AppTheme.colors.tabSelectedText else AppTheme.colors.tabUnselectedText
                                )
                            }
                        }
                    }
                }
            }

            // Global Error Banner (shown on both tabs if there is an active error)
            if (runtimeState.lastError.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppTheme.colors.statusDangerBg,
                    border = BorderStroke(1.dp, AppTheme.colors.statusDangerText.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = AppTheme.colors.statusDangerText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "组网运行提示 / 错误",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.statusDangerText
                            )
                            Text(
                                text = runtimeState.lastError,
                                fontSize = 11.sp,
                                color = AppTheme.colors.statusDangerText
                            )
                        }
                        IconButton(
                            onClick = { EasyTierRepository.clearLastError() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除",
                                tint = AppTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (activeTab == 0) {
                // Running Config Modified Banner (When user edited config while running)
                if (isCurrentProfileRunning && isConfigModified) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.statusWarningBg,
                        border = BorderStroke(1.dp, AppTheme.colors.statusWarningText.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AppTheme.colors.statusWarningText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "组网配置已修改",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.statusWarningText
                                )
                                Text(
                                    text = "当前节点仍在使用修改前的旧配置运行，需重启后生效",
                                    fontSize = 11.sp,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { launchRestartEasyTier(switchToStatusTab = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("立即重启", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Other Profile Running Banner
                if (isOtherProfileRunning) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.statusInfoBg,
                        border = BorderStroke(1.dp, AppTheme.colors.statusInfoText.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AppTheme.colors.statusInfoText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "其他网络正在运行中",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.statusInfoText
                                )
                                Text(
                                    text = "当前后台正在运行 [${runningProfile?.instanceName}]，点击下方按钮可切换启动当前网络",
                                    fontSize = 11.sp,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // TAB 0: 1. 网络配置 (Config Network)
                // =========================================================================

                // Basic Settings Card (基础设置)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppTheme.colors.statusInfoBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lan,
                                        contentDescription = null,
                                        tint = AppTheme.colors.statusInfoText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "基础设置",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }

                            // DHCP Toggle (Right side, matching screenshot)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "DHCP",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = currentProfile.dhcp,
                                    onCheckedChange = { checked ->
                                        updateCurrent {
                                            var updated = it.copy(dhcp = checked)
                                            if (!checked && updated.virtualIpv4.isBlank()) {
                                                updated = updated.copy(virtualIpv4 = "10.144.144.1")
                                            }
                                            updated
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AppTheme.colors.brandPrimary
                                    )
                                )
                            }
                        }

                        // Virtual IPv4 + /24 CIDR Prefix Row
                        val isIpInvalid = !currentProfile.dhcp && (currentProfile.virtualIpv4.isBlank() || !EasyTierRepository.isValidIpv4(currentProfile.virtualIpv4))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = if (currentProfile.dhcp) "0.0.0.0 (由对端自动分配)" else currentProfile.virtualIpv4,
                                onValueChange = { ip ->
                                    if (!currentProfile.dhcp) {
                                        updateCurrent { it.copy(virtualIpv4 = ip) }
                                    }
                                },
                                enabled = !currentProfile.dhcp,
                                isError = isIpInvalid,
                                label = { Text("虚拟 IPv4 地址 (Virtual IPv4)") },
                                placeholder = { Text("例如: 10.144.144.1") },
                                supportingText = if (isIpInvalid) {
                                    { Text("关闭 DHCP 时必须指定有效 IPv4 地址 (例如 10.144.144.1)", color = AppTheme.colors.statusDangerText) }
                                } else null,
                                singleLine = true,
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (!currentProfile.dhcp) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppTheme.colors.surfaceVariant,
                                    border = BorderStroke(1.dp, AppTheme.colors.innerBorder),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "/ ${currentProfile.networkLength}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        Column(
                                            modifier = Modifier.padding(start = 4.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "增加掩码",
                                                tint = AppTheme.colors.textSecondary,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable {
                                                        val next = (currentProfile.networkLength + 1).coerceAtMost(32)
                                                        updateCurrent { it.copy(networkLength = next) }
                                                    }
                                            )
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "减少掩码",
                                                tint = AppTheme.colors.textSecondary,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable {
                                                        val prev = (currentProfile.networkLength - 1).coerceAtLeast(8)
                                                        updateCurrent { it.copy(networkLength = prev) }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Network Name
                        OutlinedTextField(
                            value = currentProfile.networkName,
                            onValueChange = { v -> updateCurrent { it.copy(networkName = v) } },
                            label = { Text("网络名称 (Network Name)") },
                            placeholder = { Text("easytier") },
                            singleLine = true,
                            colors = appTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Network Secret
                        OutlinedTextField(
                            value = currentProfile.networkSecret,
                            onValueChange = { v -> updateCurrent { it.copy(networkSecret = v) } },
                            label = { Text("网络密钥 (Network Secret)") },
                            placeholder = { Text("留空则不设密码") },
                            singleLine = true,
                            colors = appTextFieldColors(),
                            visualTransformation = if (showSecretPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showSecretPassword = !showSecretPassword }) {
                                    Icon(
                                        imageVector = if (showSecretPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "切换密钥可见性"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Networking Method Segmented Control (公共服务器 / 手动指定 / 独立节点)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "组网方式 (Networking Method)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textSecondary
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceVariant,
                                border = BorderStroke(1.dp, AppTheme.colors.innerBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    NetworkingMethod.entries.forEach { method ->
                                        val isSelected = currentProfile.networkingMethod == method
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = if (isSelected) AppTheme.colors.tabSelectedBg else Color.Transparent,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    updateCurrent { it.copy(networkingMethod = method) }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 9.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = AppTheme.colors.tabSelectedText,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = method.label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) AppTheme.colors.tabSelectedText else AppTheme.colors.tabUnselectedText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Networking Method Detail Area
                        when (currentProfile.networkingMethod) {
                            NetworkingMethod.PUBLIC_SERVER -> {
                                val isPublicServerBlank = currentProfile.publicServerUrl.isBlank()
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedTextField(
                                        value = currentProfile.publicServerUrl,
                                        onValueChange = { url -> updateCurrent { it.copy(publicServerUrl = url) } },
                                        isError = isPublicServerBlank,
                                        label = { Text("公共服务器地址 (Public Server)") },
                                        placeholder = { Text("tcp://public.easytier.top:11010") },
                                        supportingText = if (isPublicServerBlank) {
                                            { Text("公共服务器地址不能为空", color = AppTheme.colors.statusDangerText) }
                                        } else null,
                                        singleLine = true,
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    if (isPublicServerBlank || currentProfile.publicServerUrl.trim() != "tcp://public.easytier.top:11010") {
                                        TextButton(
                                            onClick = {
                                                updateCurrent { it.copy(publicServerUrl = "tcp://public.easytier.top:11010") }
                                            },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("恢复官方公共服务器 (public.easytier.top)", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            NetworkingMethod.MANUAL -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (currentProfile.peerUrls.isEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = AppTheme.colors.statusWarningBg,
                                            border = BorderStroke(1.dp, AppTheme.colors.statusWarningText.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = AppTheme.colors.statusWarningText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "手动指定模式需要至少添加一个对端节点 (例如 tcp://x.x.x.x:11010)，否则无法与其他设备连通",
                                                    fontSize = 12.sp,
                                                    color = AppTheme.colors.statusWarningText
                                                )
                                            }
                                        }
                                    }

                                    // Removable Peer URL Chips (Just like the screenshot!)
                                    if (currentProfile.peerUrls.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            currentProfile.peerUrls.forEach { peerUri ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = AppTheme.colors.surfaceVariant,
                                                    border = BorderStroke(1.dp, AppTheme.colors.innerBorder)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = peerUri,
                                                            fontSize = 13.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Medium,
                                                            color = AppTheme.colors.textPrimary,
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                updateCurrent {
                                                                    it.copy(peerUrls = it.peerUrls.filterNot { u -> u == peerUri })
                                                                }
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "移除对端",
                                                                tint = AppTheme.colors.textSecondary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Input to add a new Peer URL
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newPeerUrlInput,
                                            onValueChange = { newPeerUrlInput = it },
                                            label = { Text("添加对端地址 (Peer URLs)") },
                                            placeholder = { Text("例如: udp://1.2.3.4:11010") },
                                            singleLine = true,
                                            colors = appTextFieldColors(),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        FilledTonalIconButton(
                                            onClick = {
                                                val trimmed = newPeerUrlInput.trim()
                                                if (trimmed.isNotBlank()) {
                                                    val finalUri = if (!trimmed.contains("://")) "tcp://$trimmed" else trimmed
                                                    updateCurrent {
                                                        it.copy(peerUrls = (it.peerUrls + finalUri).distinct())
                                                    }
                                                    newPeerUrlInput = ""
                                                }
                                            },
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = AppTheme.colors.statusInfoBg,
                                                contentColor = AppTheme.colors.statusInfoText
                                            )
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "添加对端")
                                        }
                                    }
                                }
                            }

                            NetworkingMethod.STANDALONE -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppTheme.colors.surfaceVariant,
                                    border = BorderStroke(1.dp, AppTheme.colors.innerBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = AppTheme.colors.statusInfoText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "独立节点模式不会主动连接外部对端，仅监听本地端口等待其他节点连入。",
                                            fontSize = 12.sp,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Advanced Settings Expandable Card (高级设置)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { advancedExpanded = !advancedExpanded }
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppTheme.colors.statusPurpleBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = AppTheme.colors.statusPurpleText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "高级设置",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = buildString {
                                            append(if (currentProfile.noTun) "No-TUN 双核共存" else "独立 TUN 模式")
                                            if (currentProfile.socks5Enabled) {
                                                append(" · SOCKS5 :${currentProfile.socks5Port}")
                                            }
                                            if (currentProfile.proxyCidrs.isNotEmpty()) {
                                                append(" · 子网代理(${currentProfile.proxyCidrs.size})")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "展开高级设置",
                                tint = AppTheme.colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = advancedExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                HorizontalDivider(color = AppTheme.colors.divider)

                                // Dual-Core Bridge Section (Mihomo + EasyTier)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = AppTheme.colors.surfaceVariant,
                                    border = BorderStroke(1.dp, AppTheme.colors.tabSelectedBg),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Hub,
                                                contentDescription = null,
                                                tint = AppTheme.colors.statusInfoText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "MihoTier 双核无冲突桥接设置",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AppTheme.colors.statusInfoText
                                            )
                                        }

                                        EasyTierSwitchItem(
                                            title = "无 TUN 模式 (No-TUN)",
                                            subtitle = "由 Mihomo 接管全局 TUN，EasyTier 使用用户态协议栈避免 VPN 冲突",
                                            checked = currentProfile.noTun,
                                            onCheckedChange = { v -> updateCurrent { it.copy(noTun = v) } }
                                        )

                                        EasyTierSwitchItem(
                                            title = "启用 SOCKS5 接入网关",
                                            subtitle = "在本地暴露 SOCKS5 端口，供 Mihomo 规则分流内网流量",
                                            checked = currentProfile.socks5Enabled,
                                            onCheckedChange = { v -> updateCurrent { it.copy(socks5Enabled = v) } }
                                        )

                                        if (currentProfile.socks5Enabled) {
                                            OutlinedTextField(
                                                value = currentProfile.socks5Port.toString(),
                                                onValueChange = { s ->
                                                    val p = s.filter { it.isDigit() }.toIntOrNull() ?: 10888
                                                    updateCurrent { it.copy(socks5Port = p) }
                                                },
                                                label = { Text("SOCKS5 监听端口 (本地 127.0.0.1)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                colors = appTextFieldColors(),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                }

                                // Hostname & Device Name
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = currentProfile.hostname,
                                        onValueChange = { v -> updateCurrent { it.copy(hostname = v) } },
                                        label = { Text("主机名 (Hostname)") },
                                        placeholder = { Text("MihoTier-Node") },
                                        singleLine = true,
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = currentProfile.mtu.toString(),
                                        onValueChange = { s ->
                                            val m = s.filter { it.isDigit() }.toIntOrNull() ?: 1380
                                            updateCurrent { it.copy(mtu = m) }
                                        },
                                        label = { Text("MTU") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.width(105.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                // Default Protocol selector
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "默认连接协议 (Default Protocol)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("tcp", "udp", "wg", "ws", "wss", "quic").forEach { proto ->
                                            val selected = currentProfile.defaultProtocol.equals(proto, ignoreCase = true)
                                            FilterChip(
                                                selected = selected,
                                                onClick = { updateCurrent { it.copy(defaultProtocol = proto) } },
                                                label = { Text(proto.uppercase(), fontWeight = FontWeight.SemiBold) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = AppTheme.colors.tabSelectedBg,
                                                    selectedLabelColor = AppTheme.colors.tabSelectedText,
                                                    containerColor = AppTheme.colors.surfaceVariant,
                                                    labelColor = AppTheme.colors.textSecondary
                                                )
                                            )
                                        }
                                    }
                                }

                                // Subnet Proxy CIDRs (子网代理)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "子网代理 (Proxy Network CIDRs)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    currentProfile.proxyCidrs.forEach { cidr ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = AppTheme.colors.surfaceVariant,
                                            border = BorderStroke(1.dp, AppTheme.colors.innerBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = cidr,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                                IconButton(
                                                    onClick = {
                                                        updateCurrent {
                                                            it.copy(proxyCidrs = it.proxyCidrs.filterNot { c -> c == cidr })
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "删除子网",
                                                        tint = AppTheme.colors.textSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newProxyCidrInput,
                                            onValueChange = { newProxyCidrInput = it },
                                            label = { Text("添加暴露子网 CIDR") },
                                            placeholder = { Text("例如: 192.168.1.0/24") },
                                            singleLine = true,
                                            colors = appTextFieldColors(),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        FilledTonalIconButton(
                                            onClick = {
                                                val c = newProxyCidrInput.trim()
                                                if (c.isNotBlank()) {
                                                    updateCurrent { it.copy(proxyCidrs = (it.proxyCidrs + c).distinct()) }
                                                    newProxyCidrInput = ""
                                                }
                                            },
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = AppTheme.colors.statusInfoBg,
                                                contentColor = AppTheme.colors.statusInfoText
                                            )
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "添加子网")
                                        }
                                    }
                                }

                                // Listeners (监听地址)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "监听地址列表 (Listeners)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    currentProfile.listenerUrls.forEach { listener ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = AppTheme.colors.surfaceVariant,
                                            border = BorderStroke(1.dp, AppTheme.colors.innerBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = listener,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                                IconButton(
                                                    onClick = {
                                                        updateCurrent {
                                                            it.copy(listenerUrls = it.listenerUrls.filterNot { l -> l == listener })
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "删除监听地址",
                                                        tint = AppTheme.colors.textSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newListenerUrlInput,
                                            onValueChange = { newListenerUrlInput = it },
                                            label = { Text("添加本地监听地址") },
                                            placeholder = { Text("例如: tcp://0.0.0.0:0") },
                                            singleLine = true,
                                            colors = appTextFieldColors(),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        FilledTonalIconButton(
                                            onClick = {
                                                val l = newListenerUrlInput.trim()
                                                if (l.isNotBlank()) {
                                                    updateCurrent { it.copy(listenerUrls = (it.listenerUrls + l).distinct()) }
                                                    newListenerUrlInput = ""
                                                }
                                            },
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = AppTheme.colors.statusInfoBg,
                                                contentColor = AppTheme.colors.statusInfoText
                                            )
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "添加监听")
                                        }
                                    }
                                }

                                HorizontalDivider(color = AppTheme.colors.divider)

                                // Feature Flags Switches
                                EasyTierSwitchItem(
                                    title = "启用通信加密 (enable_encryption)",
                                    subtitle = "对节点间数据传输进行 AES-GCM / 零拷贝加密",
                                    checked = currentProfile.enableEncryption,
                                    onCheckedChange = { v -> updateCurrent { it.copy(enableEncryption = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "启用 IPv6 支持 (enable_ipv6)",
                                    subtitle = "允许通过 IPv6 地址进行节点直连与打洞",
                                    checked = currentProfile.enableIpv6,
                                    onCheckedChange = { v -> updateCurrent { it.copy(enableIpv6 = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "延迟优先路由 (latency_first)",
                                    subtitle = "优先选择最低延迟路径而非最少跳数路径",
                                    checked = currentProfile.latencyFirst,
                                    onCheckedChange = { v -> updateCurrent { it.copy(latencyFirst = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "启用用户态协议栈 (use_smoltcp)",
                                    subtitle = "为子网代理和 SOCKS5 启用内置 smoltcp 网络栈",
                                    checked = currentProfile.useSmoltcp,
                                    onCheckedChange = { v -> updateCurrent { it.copy(useSmoltcp = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "启用 KCP 代理加速 (enable_kcp_proxy)",
                                    subtitle = "通过 KCP 协议加速高丢包网络下的 TCP 流量",
                                    checked = currentProfile.enableKcpProxy,
                                    onCheckedChange = { v -> updateCurrent { it.copy(enableKcpProxy = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "启用 QUIC 代理加速 (enable_quic_proxy)",
                                    subtitle = "使用 QUIC 协议承载组网内代理流量",
                                    checked = currentProfile.enableQuicProxy,
                                    onCheckedChange = { v -> updateCurrent { it.copy(enableQuicProxy = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "禁用 P2P 直连 (disable_p2p)",
                                    subtitle = "强制所有流量通过中继服务器转发",
                                    checked = currentProfile.disableP2p,
                                    onCheckedChange = { v -> updateCurrent { it.copy(disableP2p = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "禁用 UDP 打洞 (disable_udp_hole_punching)",
                                    subtitle = "关闭 NAT UDP 打洞探测",
                                    checked = currentProfile.disableUdpHolePunching,
                                    onCheckedChange = { v -> updateCurrent { it.copy(disableUdpHolePunching = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "转发所有对端 RPC (relay_all_peer_rpc)",
                                    subtitle = "允许帮助其他组网节点中继控制面 RPC",
                                    checked = currentProfile.relayAllPeerRpc,
                                    onCheckedChange = { v -> updateCurrent { it.copy(relayAllPeerRpc = v) } }
                                )
                                EasyTierSwitchItem(
                                    title = "私有模式 (private_mode)",
                                    subtitle = "拒绝与不同网络名称/密钥的节点共享中继服务",
                                    checked = currentProfile.privateMode,
                                    onCheckedChange = { v -> updateCurrent { it.copy(privateMode = v) } }
                                )
                            }
                        }
                    }
                }

                // 3. Port Forward Expandable Card (端口转发)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { portForwardExpanded = !portForwardExpanded }
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppTheme.colors.statusWarningBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = AppTheme.colors.statusWarningText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "端口转发",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = if (currentProfile.portForwards.isEmpty()) {
                                            "将本机端口映射至虚拟内网其他节点"
                                        } else {
                                            "已配置 ${currentProfile.portForwards.size} 条端口映射规则"
                                        },
                                        fontSize = 12.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (portForwardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "展开端口转发",
                                tint = AppTheme.colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = portForwardExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(color = AppTheme.colors.divider)

                                currentProfile.portForwards.forEachIndexed { idx, pf ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AppTheme.colors.surfaceVariant,
                                        border = BorderStroke(1.dp, AppTheme.colors.innerBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    color = AppTheme.colors.statusInfoBg,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = pf.proto.uppercase(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AppTheme.colors.statusInfoText,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${pf.bindAddr} ➔ ${pf.dstAddr}",
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = AppTheme.colors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    updateCurrent {
                                                        it.copy(
                                                            portForwards = it.portForwards.filterIndexed { i, _ -> i != idx }
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "删除规则",
                                                    tint = AppTheme.colors.statusDangerText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showAddPortForwardDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AppTheme.colors.tabSelectedBg)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = AppTheme.colors.statusInfoText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "添加端口转发规则",
                                        color = AppTheme.colors.statusInfoText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Primary Run / Stop / Restart Network Pill Button
                if (isCurrentProfileRunning && isConfigModified) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { launchRestartEasyTier(switchToStatusTab = true) },
                            modifier = Modifier
                                .weight(2f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "保存并重启组网",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                EasyTierRepository.stopNetwork()
                                Toast.makeText(context, "已停止 EasyTier 组网", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.statusDangerText)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "停止",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else if (isOtherProfileRunning) {
                    Button(
                        onClick = { launchRestartEasyTier(switchToStatusTab = true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "切换并启动当前网络",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (isCurrentProfileRunning) {
                                EasyTierRepository.stopNetwork()
                                Toast.makeText(context, "已停止 EasyTier 组网", Toast.LENGTH_SHORT).show()
                            } else {
                                launchStartEasyTier(switchToStatusTab = true)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrentProfileRunning) AppTheme.colors.statusDangerText else AppTheme.colors.brandPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isCurrentProfileRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCurrentProfileRunning) "停止组网 (Stop Network)" else "启动组网 (Run Network)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // =========================================================================
                // TAB 1: 2. 运行状态 (Running Status)
                // =========================================================================
                EasyTierRunningTab(
                    context = context,
                    runtimeState = runtimeState,
                    currentProfile = currentProfile,
                    isConfigModified = isConfigModified,
                    onStartNetwork = {
                        launchStartEasyTier(switchToStatusTab = false)
                    },
                    onRestartNetwork = {
                        launchRestartEasyTier(switchToStatusTab = false)
                    },
                    onStopNetwork = {
                        EasyTierRepository.stopNetwork()
                        Toast.makeText(context, "已停止 EasyTier 组网", Toast.LENGTH_SHORT).show()
                    },
                    onNavigateToLogs = onNavigateToLogs
                )
            }
        }
    }

    // Create New Profile Dialog
    if (showCreateProfileDialog) {
        var nameInput by remember { mutableStateOf("easytier-${profiles.size + 1}") }
        AlertDialog(
            onDismissRequest = { showCreateProfileDialog = false },
            title = { Text("新建组网实例", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("实例名称 (Instance Name)") },
                    singleLine = true,
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        EasyTierRepository.createProfile(context, nameInput)
                        showCreateProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary)
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProfileDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Rename Current Profile Dialog
    if (showRenameProfileDialog) {
        var nameInput by remember { mutableStateOf(currentProfile.instanceName) }
        AlertDialog(
            onDismissRequest = { showRenameProfileDialog = false },
            title = { Text("重命名组网实例", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("实例名称 (Instance Name)") },
                    singleLine = true,
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            updateCurrent { it.copy(instanceName = nameInput.trim()) }
                        }
                        showRenameProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameProfileDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Delete Profile Confirm Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("删除当前网络", fontWeight = FontWeight.Bold) },
            text = {
                Text("确定要删除组网实例「${currentProfile.instanceName}」吗？此操作无法撤销。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        EasyTierRepository.deleteProfile(context, currentProfile.id)
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "已删除组网配置", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.statusDangerText)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Add Port Forward Rule Dialog
    if (showAddPortForwardDialog) {
        var proto by remember { mutableStateOf("tcp") }
        var bindAddr by remember { mutableStateOf("0.0.0.0:8080") }
        var dstAddr by remember { mutableStateOf("10.144.144.2:80") }

        AlertDialog(
            onDismissRequest = { showAddPortForwardDialog = false },
            title = { Text("添加端口转发规则", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("tcp", "udp").forEach { p ->
                            FilterChip(
                                selected = proto == p,
                                onClick = { proto = p },
                                label = { Text(p.uppercase(), fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppTheme.colors.tabSelectedBg,
                                    selectedLabelColor = AppTheme.colors.tabSelectedText,
                                    containerColor = AppTheme.colors.surfaceVariant,
                                    labelColor = AppTheme.colors.textSecondary
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = bindAddr,
                        onValueChange = { bindAddr = it },
                        label = { Text("本地监听地址 (bind_addr)") },
                        placeholder = { Text("0.0.0.0:8080") },
                        singleLine = true,
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = dstAddr,
                        onValueChange = { dstAddr = it },
                        label = { Text("目标内网地址 (dst_addr)") },
                        placeholder = { Text("10.144.144.2:80") },
                        singleLine = true,
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bindAddr.contains(":") && dstAddr.contains(":")) {
                            updateCurrent {
                                it.copy(
                                    portForwards = it.portForwards + EasyTierPortForward(
                                        proto = proto,
                                        bindAddr = bindAddr.trim(),
                                        dstAddr = dstAddr.trim()
                                    )
                                )
                            }
                            showAddPortForwardDialog = false
                        } else {
                            Toast.makeText(context, "请填写完整的 IP:端口 格式", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary)
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPortForwardDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Full-screen TOML Config Editor / Inspector Modal
    if (showTomlEditorSheet) {
        EasyTierTomlEditorDialog(
            profile = currentProfile,
            onDismiss = { showTomlEditorSheet = false },
            onSave = { useCustom, customTomlText ->
                updateCurrent {
                    it.copy(
                        useCustomToml = useCustom,
                        customToml = customTomlText
                    )
                }
                showTomlEditorSheet = false
                Toast.makeText(context, "已保存 TOML 配置", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dual-Core Mihomo + EasyTier Linkage Guide Dialog
    if (showDualCoreGuideDialog) {
        val socksPort = currentProfile.socks5Port
        val sampleYaml = """
            proxies:
              - name: "EasyTier-内网网关"
                type: socks5
                server: 127.0.0.1
                port: $socksPort
                udp: true

            rules:
              - IP-CIDR,10.144.144.0/24,EasyTier-内网网关,no-resolve
        """.trimIndent()

        AlertDialog(
            onDismissRequest = { showDualCoreGuideDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = AppTheme.colors.statusInfoText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MihoTier 双核无冲突联动说明", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Android 系统同一时间仅允许建立一张 VpnService 虚拟网卡。MihoTier 通过以下架构实现 Mihomo 与 EasyTier 同时在线：",
                        fontSize = 13.sp,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = "1. EasyTier 开启 No-TUN (用户态协议栈) 并在本地监听 SOCKS5 端口 (127.0.0.1:$socksPort)。\n2. Mihomo 接管系统 TUN 并通过 IP-CIDR 规则将虚拟内网网段 (如 10.144.144.0/24) 自动分流至 EasyTier SOCKS5 网关。",
                        fontSize = 12.sp,
                        color = AppTheme.colors.textSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppTheme.colors.terminalBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sampleYaml,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = AppTheme.colors.terminalText,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Mihomo EasyTier YAML", sampleYaml))
                        Toast.makeText(context, "已复制 Mihomo 分流配置片段", Toast.LENGTH_SHORT).show()
                        showDualCoreGuideDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary)
                ) {
                    Text("复制 YAML 片段")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDualCoreGuideDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun EasyTierRunningTab(
    context: Context,
    runtimeState: top.xing2387.mihotier.core.config.EasyTierRuntimeState,
    currentProfile: EasyTierNetworkProfile,
    isConfigModified: Boolean = false,
    onStartNetwork: () -> Unit,
    onRestartNetwork: () -> Unit = {},
    onStopNetwork: () -> Unit,
    onNavigateToLogs: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (runtimeState.isRunning && isConfigModified) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppTheme.colors.statusWarningBg,
                border = BorderStroke(1.dp, AppTheme.colors.statusWarningText.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AppTheme.colors.statusWarningText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "配置已修改，需重启以应用",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.statusWarningText
                        )
                        Text(
                            text = "当前运行中的节点仍在使用旧参数，点击立即重启应用新配置",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onRestartNetwork,
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("立即重启", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Local Node Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(
                1.dp,
                if (runtimeState.isRunning) AppTheme.colors.statusSuccessBorder else AppTheme.colors.cardBorder
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (runtimeState.isRunning) AppTheme.colors.statusSuccessBg else AppTheme.colors.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = if (runtimeState.isRunning) AppTheme.colors.statusSuccessText else AppTheme.colors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (runtimeState.isRunning) "本机节点运行中" else "组网未启动",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = if (runtimeState.isRunning) {
                                    "实例: ${runtimeState.runningProfileName} · 已运行 ${runtimeState.uptimeSec}s"
                                } else {
                                    "点击下方按钮启动当前选中的组网实例"
                                },
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }

                    Button(
                        onClick = { if (runtimeState.isRunning) onStopNetwork() else onStartNetwork() },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (runtimeState.isRunning) Color(0xFFDC2626) else AppTheme.colors.brandPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (runtimeState.isRunning) "停止" else "启动",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                HorizontalDivider(color = AppTheme.colors.divider)

                // 2x2 Grid of Local Node Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "本机虚拟 IPv4",
                        value = if (runtimeState.isRunning) runtimeState.allocatedIpv4 else "未分配",
                        highlight = runtimeState.isRunning
                    )
                    StatusMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "组网名称",
                        value = if (runtimeState.isRunning) runtimeState.networkName else currentProfile.networkName
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "SOCKS5 桥接网关",
                        value = if (runtimeState.isRunning) runtimeState.socks5Portal else "127.0.0.1:${currentProfile.socks5Port}"
                    )
                    StatusMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "NAT 穿透类型",
                        value = if (runtimeState.isRunning) runtimeState.natType else "待检测"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "实时下行 (累计 ${top.xing2387.mihotier.core.config.BandwidthFormatter.formatTotalBytes(runtimeState.rxTotalBytes)})",
                        value = "↓ ${top.xing2387.mihotier.core.config.BandwidthFormatter.formatAdaptive(runtimeState.rxSpeed)}",
                        highlight = runtimeState.isRunning && runtimeState.rxSpeed > 0L
                    )
                    StatusMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "实时上行 (累计 ${top.xing2387.mihotier.core.config.BandwidthFormatter.formatTotalBytes(runtimeState.txTotalBytes)})",
                        value = "↑ ${top.xing2387.mihotier.core.config.BandwidthFormatter.formatAdaptive(runtimeState.txSpeed)}",
                        highlight = runtimeState.isRunning && runtimeState.txSpeed > 0L
                    )
                }
            }
        }

        // 2. Connected Peers List Card (对端节点列表)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val directCount = runtimeState.peers.count { !it.isRelay }
                val relayCount = runtimeState.peers.count { it.isRelay }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "组网节点 (Peers)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        if (runtimeState.isRunning && runtimeState.peers.isNotEmpty()) {
                            Text(
                                text = "全网拓扑: $directCount 直连 · $relayCount 中继",
                                fontSize = 11.sp,
                                color = AppTheme.colors.textMuted
                            )
                        }
                    }
                    Surface(
                        color = if (directCount > 0) AppTheme.colors.statusSuccessBg else AppTheme.colors.statusInfoBg,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (relayCount > 0) "$directCount 直连 · $relayCount 中继" else "$directCount 个直连",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (directCount > 0) AppTheme.colors.statusSuccessText else AppTheme.colors.statusInfoText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (!runtimeState.isRunning || runtimeState.peers.isEmpty()) {
                    Text(
                        text = if (runtimeState.isRunning) "独立节点模式或正在探测对端节点..." else "启动组网后在此查看已连接的对端节点详情",
                        fontSize = 13.sp,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    runtimeState.peers.forEach { peer ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AppTheme.colors.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (peer.isRelay) AppTheme.colors.innerBorder else AppTheme.colors.statusSuccessBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = peer.hostname,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = if (peer.isRelay) AppTheme.colors.surfaceVariant else AppTheme.colors.statusSuccessBg,
                                            shape = RoundedCornerShape(6.dp),
                                            border = if (peer.isRelay) BorderStroke(1.dp, AppTheme.colors.innerBorder) else null
                                        ) {
                                            Text(
                                                text = if (peer.isRelay) peer.tunnelProto else "${peer.tunnelProto} 直连",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (peer.isRelay) AppTheme.colors.textSecondary else AppTheme.colors.statusSuccessText,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            color = AppTheme.colors.cardBackground,
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, AppTheme.colors.innerBorder)
                                        ) {
                                            Text(
                                                text = "${peer.latencyMs} ms",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AppTheme.colors.textPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "虚拟IP: ${peer.virtualIpv4}",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = AppTheme.colors.textSecondary
                                        )
                                        if (peer.isRelay) {
                                            Surface(
                                                color = AppTheme.colors.statusWarningBg,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "中继路由",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = AppTheme.colors.statusWarningText,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (peer.isPublicServer) {
                                            Surface(
                                                color = AppTheme.colors.statusInfoBg,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "公共节点",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = AppTheme.colors.statusInfoText,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "↓${peer.rxBytes} ↑${peer.txBytes}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AppTheme.colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusMetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) AppTheme.colors.statusSuccessBg.copy(alpha = if (AppTheme.colors.isDark) 0.35f else 0.8f) else AppTheme.colors.surfaceVariant,
        border = BorderStroke(1.dp, if (highlight) AppTheme.colors.statusSuccessBorder else AppTheme.colors.innerBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = AppTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (highlight) AppTheme.colors.statusSuccessText else AppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EasyTierSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = AppTheme.colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppTheme.colors.brandPrimary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EasyTierTomlEditorDialog(
    profile: EasyTierNetworkProfile,
    onDismiss: () -> Unit,
    onSave: (useCustom: Boolean, customToml: String) -> Unit
) {
    val context = LocalContext.current
    val generatedToml = remember(profile) {
        profile.copy(useCustomToml = false).toTomlString()
    }
    var useCustom by remember { mutableStateOf(profile.useCustomToml) }
    var tomlText by remember {
        mutableStateOf(
            if (profile.useCustomToml && profile.customToml.isNotBlank()) profile.customToml else generatedToml
        )
    }
    var validationResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "EasyTier TOML 配置文件",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AppTheme.colors.textPrimary
                )
                TextButton(
                    onClick = {
                        val err = EasyTierRepository.validateToml(tomlText)
                        validationResult = if (err.isBlank()) "✓ Rust easytier-core 语法校验通过！" else err
                    }
                ) {
                    Text(
                        "校验语法",
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.brandPrimary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "使用自定义 TOML 覆盖表单",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "开启后直接以文本内容启动 EasyTier",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                    Switch(
                        checked = useCustom,
                        onCheckedChange = {
                            useCustom = it
                            if (!it) {
                                tomlText = generatedToml
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppTheme.colors.brandPrimary
                        )
                    )
                }

                validationResult?.let { msg ->
                    val isOk = msg.startsWith("✓")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOk) AppTheme.colors.statusSuccessBg else AppTheme.colors.statusDangerBg,
                        border = BorderStroke(1.dp, if (isOk) AppTheme.colors.statusSuccessBorder else AppTheme.colors.innerBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isOk) AppTheme.colors.statusSuccessText else AppTheme.colors.statusDangerText,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = tomlText,
                    onValueChange = {
                        tomlText = it
                        useCustom = true
                        validationResult = null
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = AppTheme.colors.textPrimary
                    ),
                    colors = appTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 380.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = EasyTierRepository.validateToml(tomlText)
                    if (err.isNotBlank()) {
                        validationResult = err
                        Toast.makeText(context, "TOML 语法校验未通过，请检查配置", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(useCustom, tomlText)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary)
            ) {
                Text("保存配置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
