package top.xing2387.mihotier.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyTierScreen(
    onNavigateBack: () -> Unit
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

    val isCurrentProfileRunning = runtimeState.isRunning && runtimeState.runningProfileId == currentProfile.id

    fun updateCurrent(transform: (EasyTierNetworkProfile) -> EasyTierNetworkProfile) {
        val updated = transform(currentProfile)
        EasyTierRepository.updateProfile(context, updated)
    }

    Scaffold(
        containerColor = Color(0xFFEEF3F8),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EasyTier 异地组网",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (runtimeState.isRunning) Color(0xFFD1FAE5) else Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (runtimeState.isRunning) "RUNNING" else "CORE v2",
                                color = if (runtimeState.isRunning) Color(0xFF065F46) else Color(0xFF0369A1),
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
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDualCoreGuideDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "双核桥接指南",
                            tint = Color(0xFF0284C7)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEEF3F8)
                )
            )
        },
        bottomBar = {
            if (activeTab == 0) {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF104179))
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
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "自定义 TOML",
                                        color = Color(0xFFB45309),
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
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
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
                    color = Color(0xFF104179),
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
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (isCurrentProfileRunning) Color(0xFFD1FAE5) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = if (isCurrentProfileRunning) "running" else "stopped",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCurrentProfileRunning) Color(0xFF065F46) else Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "切换配置",
                                tint = Color(0xFF475569)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showProfileDropdown,
                        onDismissRequest = { showProfileDropdown = false },
                        modifier = Modifier.background(Color.White)
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
                                            color = Color(0xFF0F172A)
                                        )
                                        Surface(
                                            color = if (isItemRunning) Color(0xFFD1FAE5) else Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Text(
                                                text = if (isItemRunning) "running" else "stopped",
                                                fontSize = 10.sp,
                                                color = if (isItemRunning) Color(0xFF065F46) else Color(0xFF64748B),
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
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showRenameProfileDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "实例设置",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // 2. Step Segmented Switcher: [ 1. 网络配置 ] | [ 2. 运行状态 ]
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                            color = if (selected) Color(0xFFBAE6FD) else Color.Transparent,
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
                                            .background(Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color(0xFF075985) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }

            if (activeTab == 0) {
                // =========================================================================
                // TAB 0: 1. 网络配置 (Config Network)
                // =========================================================================

                // Basic Settings Card (基础设置)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                                        .background(Color(0xFFE0F2FE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lan,
                                        contentDescription = null,
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "基础设置",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            // DHCP Toggle (Right side, matching screenshot)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "DHCP",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = currentProfile.dhcp,
                                    onCheckedChange = { checked ->
                                        updateCurrent { it.copy(dhcp = checked) }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF104179)
                                    )
                                )
                            }
                        }

                        // Virtual IPv4 + /24 CIDR Prefix Row
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
                                label = { Text("虚拟 IPv4 地址 (Virtual IPv4)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (!currentProfile.dhcp) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                                            color = Color(0xFF0F172A)
                                        )
                                        Column(
                                            modifier = Modifier.padding(start = 4.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "增加掩码",
                                                tint = Color(0xFF475569),
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
                                                tint = Color(0xFF475569),
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
                                color = Color(0xFF475569)
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                            color = if (isSelected) Color(0xFFBAE6FD) else Color.Transparent,
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
                                                        tint = Color(0xFF075985),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = method.label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF075985) else Color(0xFF64748B)
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
                                OutlinedTextField(
                                    value = currentProfile.publicServerUrl,
                                    onValueChange = { url -> updateCurrent { it.copy(publicServerUrl = url) } },
                                    label = { Text("公共服务器地址 (Public Server)") },
                                    placeholder = { Text("tcp://public.easytier.top:11010") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            NetworkingMethod.MANUAL -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Removable Peer URL Chips (Just like the screenshot!)
                                    if (currentProfile.peerUrls.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            currentProfile.peerUrls.forEach { peerUri ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFFF8FAFC),
                                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
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
                                                            color = Color(0xFF1E293B),
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
                                                                tint = Color(0xFF64748B),
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
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        FilledTonalIconButton(
                                            onClick = {
                                                val trimmed = newPeerUrlInput.trim()
                                                if (trimmed.isNotBlank()) {
                                                    if (!trimmed.contains("://")) {
                                                        Toast.makeText(context, "请包含协议头，如 tcp:// 或 udp://", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        updateCurrent {
                                                            it.copy(peerUrls = (it.peerUrls + trimmed).distinct())
                                                        }
                                                        newPeerUrlInput = ""
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = Color(0xFFE0F2FE),
                                                contentColor = Color(0xFF0369A1)
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
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "独立节点模式不会主动连接外部对端，仅监听本地端口等待其他节点连入。",
                                            fontSize = 12.sp,
                                            color = Color(0xFF475569)
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
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                                        .background(Color(0xFFEDE9FE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = Color(0xFF6D28D9),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "高级设置",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
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
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "展开高级设置",
                                tint = Color(0xFF64748B)
                            )
                        }

                        AnimatedVisibility(visible = advancedExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                // Dual-Core Bridge Section (Mihomo + EasyTier)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF0F9FF),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
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
                                                tint = Color(0xFF0369A1),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "MihoTier 双核无冲突桥接设置",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF075985)
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
                                        color = Color(0xFF475569)
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
                                                    selectedContainerColor = Color(0xFFBAE6FD),
                                                    selectedLabelColor = Color(0xFF075985)
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
                                        color = Color(0xFF475569)
                                    )
                                    currentProfile.proxyCidrs.forEach { cidr ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF8FAFC),
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
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
                                                    color = Color(0xFF1E293B)
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
                                                        tint = Color(0xFF64748B),
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
                                            shape = RoundedCornerShape(12.dp)
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
                                        color = Color(0xFF475569)
                                    )
                                    currentProfile.listenerUrls.forEach { listener ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF8FAFC),
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
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
                                                    color = Color(0xFF1E293B)
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
                                                        tint = Color(0xFF64748B),
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
                                            placeholder = { Text("例如: tcp://0.0.0.0:11010") },
                                            singleLine = true,
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
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "添加监听")
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

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
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                                        .background(Color(0xFFFEF3C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "端口转发",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = if (currentProfile.portForwards.isEmpty()) {
                                            "将本机端口映射至虚拟内网其他节点"
                                        } else {
                                            "已配置 ${currentProfile.portForwards.size} 条端口映射规则"
                                        },
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (portForwardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "展开端口转发",
                                tint = Color(0xFF64748B)
                            )
                        }

                        AnimatedVisibility(visible = portForwardExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                currentProfile.portForwards.forEachIndexed { idx, pf ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                                                    color = Color(0xFFE0F2FE),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = pf.proto.uppercase(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF0369A1),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${pf.bindAddr} ➔ ${pf.dstAddr}",
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF1E293B),
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
                                                    tint = Color(0xFFEF4444),
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
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "添加端口转发规则",
                                        color = Color(0xFF0284C7),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Primary Run / Stop Network Pill Button (Matches Screenshot!)
                Button(
                    onClick = {
                        if (isCurrentProfileRunning) {
                            EasyTierRepository.stopNetwork()
                            Toast.makeText(context, "已停止 EasyTier 组网", Toast.LENGTH_SHORT).show()
                        } else {
                            val res = EasyTierRepository.startNetwork(context, currentProfile)
                            if (res.isSuccess) {
                                Toast.makeText(context, "EasyTier 组网已启动", Toast.LENGTH_SHORT).show()
                                activeTab = 1
                            } else {
                                Toast.makeText(
                                    context,
                                    "启动失败: ${res.exceptionOrNull()?.message ?: "配置有误"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentProfileRunning) Color(0xFFDC2626) else Color(0xFF104179)
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

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // =========================================================================
                // TAB 1: 2. 运行状态 (Running Status)
                // =========================================================================
                EasyTierRunningTab(
                    context = context,
                    runtimeState = runtimeState,
                    currentProfile = currentProfile,
                    onStartNetwork = {
                        val res = EasyTierRepository.startNetwork(context, currentProfile)
                        if (res.isSuccess) {
                            Toast.makeText(context, "EasyTier 组网已启动", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "启动失败: ${res.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onStopNetwork = {
                        EasyTierRepository.stopNetwork()
                        Toast.makeText(context, "已停止 EasyTier 组网", Toast.LENGTH_SHORT).show()
                    }
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF104179))
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF104179))
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
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
                                label = { Text(p.uppercase(), fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = bindAddr,
                        onValueChange = { bindAddr = it },
                        label = { Text("本地监听地址 (bind_addr)") },
                        placeholder = { Text("0.0.0.0:8080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = dstAddr,
                        onValueChange = { dstAddr = it },
                        label = { Text("目标内网地址 (dst_addr)") },
                        placeholder = { Text("10.144.144.2:80") },
                        singleLine = true,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF104179))
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
                        tint = Color(0xFF0284C7)
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
                        color = Color(0xFF334155)
                    )
                    Text(
                        text = "1. EasyTier 开启 No-TUN (用户态协议栈) 并在本地监听 SOCKS5 端口 (127.0.0.1:$socksPort)。\n2. Mihomo 接管系统 TUN 并通过 IP-CIDR 规则将虚拟内网网段 (如 10.144.144.0/24) 自动分流至 EasyTier SOCKS5 网关。",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sampleYaml,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF104179))
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
    onStartNetwork: () -> Unit,
    onStopNetwork: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Local Node Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(
                1.dp,
                if (runtimeState.isRunning) Color(0xFF6EE7B7) else Color(0xFFE2E8F0)
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
                                    if (runtimeState.isRunning) Color(0xFFD1FAE5) else Color(0xFFF1F5F9)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = if (runtimeState.isRunning) Color(0xFF059669) else Color(0xFF64748B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (runtimeState.isRunning) "本机节点运行中" else "组网未启动",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (runtimeState.isRunning) {
                                    "实例: ${runtimeState.runningProfileName} · 已运行 ${runtimeState.uptimeSec}s"
                                } else {
                                    "点击下方按钮启动当前选中的组网实例"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Button(
                        onClick = { if (runtimeState.isRunning) onStopNetwork() else onStartNetwork() },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (runtimeState.isRunning) Color(0xFFDC2626) else Color(0xFF104179)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (runtimeState.isRunning) "停止" else "启动",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

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
            }
        }

        // 2. Connected Peers List Card (对端节点列表)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "对端节点 (Peers)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Surface(
                        color = Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "${runtimeState.peers.size} 个连接",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (!runtimeState.isRunning || runtimeState.peers.isEmpty()) {
                    Text(
                        text = if (runtimeState.isRunning) "独立节点模式或正在探测对端节点..." else "启动组网后在此查看已连接的对端节点详情",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    runtimeState.peers.forEach { peer ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                    Text(
                                        text = peer.hostname,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = Color(0xFFE0F2FE),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = peer.tunnelProto,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0369A1),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFFD1FAE5),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${peer.latencyMs} ms",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF065F46),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "虚拟 IP: ${peer.virtualIpv4}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        text = "↓ ${peer.rxBytes}  ↑ ${peer.txBytes}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Event Logs Card (运行事件日志)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "实例事件日志 (Events)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (runtimeState.eventLogs.isEmpty()) {
                            Text(
                                text = "暂无事件日志...",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8)
                            )
                        } else {
                            runtimeState.eventLogs.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFE2E8F0),
                                    lineHeight = 16.sp
                                )
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
        color = if (highlight) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (highlight) Color(0xFFBBF7D0) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (highlight) Color(0xFF065F46) else Color(0xFF0F172A),
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
                color = Color(0xFF0F172A)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF104179)
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
                Text("EasyTier TOML 配置文件", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                TextButton(
                    onClick = {
                        val err = EasyTierRepository.validateToml(tomlText)
                        validationResult = if (err.isBlank()) "✓ Rust easytier-core 语法校验通过！" else err
                    }
                ) {
                    Text("校验语法", fontWeight = FontWeight.Bold)
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
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "开启后直接以文本内容启动 EasyTier",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = useCustom,
                        onCheckedChange = {
                            useCustom = it
                            if (!it) {
                                tomlText = generatedToml
                            }
                        }
                    )
                }

                validationResult?.let { msg ->
                    val isOk = msg.startsWith("✓")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOk) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isOk) Color(0xFF065F46) else Color(0xFF991B1B),
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
                        fontSize = 12.sp
                    ),
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF104179))
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
