package top.xing2387.mihotier.ui.screens

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.EasyTierRepository
import top.xing2387.mihotier.core.config.MetaFeaturesSettingsRepository
import top.xing2387.mihotier.core.config.OverrideSettingsRepository
import top.xing2387.mihotier.core.config.ProfileRepository
import top.xing2387.mihotier.core.vpn.UnifiedVpnService
import top.xing2387.mihotier.ui.theme.AppTheme
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AppSelectionScreen(
    isRunning: Boolean = false,
    trafficStats: UnifiedVpnService.TrafficStats = UnifiedVpnService.TrafficStats(),
    onToggleVpn: () -> Unit = {},
    onNavigateToClash: () -> Unit,
    onNavigateToEasyTier: () -> Unit,
    onNavigateToProxy: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToOverride: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ProfileRepository.init(context)
        EasyTierRepository.init(context)
    }

    val easyTierProfiles by EasyTierRepository.profiles.collectAsState()
    val easyTierSelectedId by EasyTierRepository.selectedProfileId.collectAsState()
    val easyTierRuntime by EasyTierRepository.runtimeState.collectAsState()
    val currentEasyTierProfile = remember(easyTierProfiles, easyTierSelectedId) {
        easyTierProfiles.find { it.id == easyTierSelectedId } ?: easyTierProfiles.firstOrNull()
    }

    var isEasyTierTransitioning by remember { mutableStateOf(false) }
    var easyTierTargetRunning by remember { mutableStateOf<Boolean?>(null) }

    fun startEasyTierNow() {
        if (isEasyTierTransitioning) return
        val profile = currentEasyTierProfile
        if (profile == null) {
            Toast.makeText(context, "未找到 EasyTier 配置", Toast.LENGTH_SHORT).show()
            return
        }
        isEasyTierTransitioning = true
        easyTierTargetRunning = true
        coroutineScope.launch {
            val res = withContext(Dispatchers.IO) {
                EasyTierRepository.startNetwork(context, profile)
            }
            isEasyTierTransitioning = false
            easyTierTargetRunning = null
            if (res.isSuccess) {
                Toast.makeText(context, "EasyTier 组网已启动", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    "启动失败: ${res.exceptionOrNull()?.message ?: "配置有误"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val easyTierVpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startEasyTierNow()
        } else {
            Toast.makeText(context, "未授予 VPN 权限，无法建立虚拟网卡", Toast.LENGTH_SHORT).show()
        }
    }

    fun onToggleEasyTier() {
        if (isEasyTierTransitioning) return
        if (easyTierRuntime.isRunning) {
            isEasyTierTransitioning = true
            easyTierTargetRunning = false
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    EasyTierRepository.stopNetwork()
                }
                isEasyTierTransitioning = false
                easyTierTargetRunning = null
                Toast.makeText(context, "EasyTier 组网已停止", Toast.LENGTH_SHORT).show()
            }
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                easyTierVpnPermissionLauncher.launch(prepareIntent)
            } else {
                startEasyTierNow()
            }
        }
    }

    val profiles by ProfileRepository.profiles.collectAsState()
    val activeProfile = remember(profiles) {
        ProfileRepository.getActiveProfile() ?: profiles.firstOrNull()
    }
    val activeProfileName = remember(activeProfile) {
        activeProfile?.name?.ifBlank { "默认配置" } ?: "内置默认配置"
    }

    val overrideSettings = remember { OverrideSettingsRepository.load(context) }
    val metaSettings = remember { MetaFeaturesSettingsRepository.load(context) }

    var proxyModeText by remember { mutableStateOf("规则分流 (Rule)") }
    var currentNodeName by remember { mutableStateOf(if (isRunning) "获取中..." else "未连接") }
    var currentNodeLatency by remember { mutableStateOf<Int?>(null) }

    // Poll Mihomo live node & mode when running
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()

            while (isActive) {
                withContext(Dispatchers.IO) {
                    try {
                        val configReq = Request.Builder().url("http://127.0.0.1:9090/configs").get().build()
                        httpClient.newCall(configReq).execute().use { res ->
                            if (res.isSuccessful) {
                                val json = JSONObject(res.body?.string() ?: "")
                                val rawM = json.optString("mode", "rule")
                                val m = if (EasyTierRepository.getBridgeRoutingInfo(context).enabled &&
                                    ConfigManager.getConfiguredClashMode(context) == "global"
                                ) {
                                    "global"
                                } else {
                                    rawM
                                }
                                proxyModeText = when (m.lowercase()) {
                                    "global" -> "全局代理 (Global)"
                                    "direct" -> "直连模式 (Direct)"
                                    else -> "规则分流 (Rule)"
                                }
                            }
                        }

                        val proxiesReq = Request.Builder().url("http://127.0.0.1:9090/proxies").get().build()
                        httpClient.newCall(proxiesReq).execute().use { res ->
                            if (res.isSuccessful) {
                                val json = JSONObject(res.body?.string() ?: "")
                                val proxiesObj = json.optJSONObject("proxies")
                                if (proxiesObj != null) {
                                    val group = proxiesObj.optJSONObject("GLOBAL") ?: proxiesObj.optJSONObject("Proxy")
                                    val active = group?.optString("now")
                                    if (!active.isNullOrBlank()) {
                                        currentNodeName = active
                                        val nodeObj = proxiesObj.optJSONObject(active)
                                        val hist = nodeObj?.optJSONArray("history")
                                        if (hist != null && hist.length() > 0) {
                                            val lastH = hist.getJSONObject(hist.length() - 1)
                                            val d = lastH.optInt("delay", 0)
                                            currentNodeLatency = if (d > 0) d else null
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                delay(2500)
            }
        } else {
            currentNodeName = "未连接"
            currentNodeLatency = null
        }
    }

    val upSpeedBytes = when {
        isRunning -> trafficStats.upSpeed
        easyTierRuntime.isRunning -> easyTierRuntime.txSpeed
        else -> 0L
    }
    val downSpeedBytes = when {
        isRunning -> trafficStats.downSpeed
        easyTierRuntime.isRunning -> easyTierRuntime.rxSpeed
        else -> 0L
    }
    val totalUpBytes = when {
        isRunning -> trafficStats.upTotal
        easyTierRuntime.isRunning -> easyTierRuntime.txTotalBytes
        else -> 0L
    }
    val totalDownBytes = when {
        isRunning -> trafficStats.downTotal
        easyTierRuntime.isRunning -> easyTierRuntime.rxTotalBytes
        else -> 0L
    }

    Scaffold(
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Top Brand & Global Tunnel Status Header (Fixed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppTheme.colors.brandPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "MihoTier",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MihoTier",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "状态总览",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }

                // Tunnel status pill
                val anyTunActive = isRunning || easyTierRuntime.isRunning
                val tunStatusLabel = when {
                    isRunning && easyTierRuntime.isRunning -> "双核 TUN 运行中"
                    isRunning -> "Mihomo TUN 运行中"
                    easyTierRuntime.isRunning -> "EasyTier TUN 运行中"
                    else -> "TUN 待机中"
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (anyTunActive) AppTheme.colors.statusSuccessBg else AppTheme.colors.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (anyTunActive) AppTheme.colors.statusSuccessText else AppTheme.colors.textMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tunStatusLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (anyTunActive) AppTheme.colors.statusSuccessText else AppTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 2. Dual-Core Bridge & Real-time Traffic Summary Card
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
                    // Topology Strip:
                    val easyTierOnly = !isRunning && easyTierRuntime.isRunning
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TopologyNodeChip(
                            label = "Android TUN",
                            active = isRunning || easyTierRuntime.isRunning,
                            activeBg = AppTheme.colors.statusInfoBg,
                            activeText = AppTheme.colors.statusInfoText
                        )
                        if (isRunning || easyTierRuntime.isRunning) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = AppTheme.colors.statusInfoText,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(16.dp))
                        }
                        if (easyTierOnly) {
                            TopologyNodeChip(
                                label = "EasyTier 组网",
                                active = true,
                                activeBg = AppTheme.colors.statusSuccessBg,
                                activeText = AppTheme.colors.statusSuccessText
                            )
                            Spacer(modifier = Modifier.size(16.dp))
                            TopologyNodeChip(
                                label = "Mihomo 核心",
                                active = false,
                                activeBg = AppTheme.colors.statusSuccessBg,
                                activeText = AppTheme.colors.statusSuccessText
                            )
                        } else {
                            TopologyNodeChip(
                                label = "Mihomo 核心",
                                active = isRunning,
                                activeBg = AppTheme.colors.statusSuccessBg,
                                activeText = AppTheme.colors.statusSuccessText
                            )
                            if (isRunning && easyTierRuntime.isRunning) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = AppTheme.colors.statusSuccessText,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.size(16.dp))
                            }
                            TopologyNodeChip(
                                label = "EasyTier 组网",
                                active = easyTierRuntime.isRunning,
                                activeBg = AppTheme.colors.statusSuccessBg,
                                activeText = AppTheme.colors.statusSuccessText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Real-time Speeds Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Download Speed Box
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppTheme.colors.statusInfoBg.copy(alpha = if (AppTheme.colors.isDark) 0.35f else 0.7f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppTheme.colors.statusInfoBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Download",
                                    tint = AppTheme.colors.statusInfoText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "累计 ${formatBytes(totalDownBytes)}",
                                    fontSize = 11.sp,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = formatSpeed(downSpeedBytes),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }

                        // Upload Speed Box
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppTheme.colors.statusSuccessBg.copy(alpha = if (AppTheme.colors.isDark) 0.35f else 0.7f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppTheme.colors.statusSuccessBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Upload",
                                    tint = AppTheme.colors.statusSuccessText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "累计 ${formatBytes(totalUpBytes)}",
                                    fontSize = 11.sp,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = formatSpeed(upSpeedBytes),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Title
            Text(
                text = "核心引擎控制台 (点击卡片进入详情页)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            // 3. Large Card 1: Mihomo (Clash.Meta) Overview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToClash() },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(
                    width = if (isRunning) 1.5.dp else 1.dp,
                    color = if (isRunning) AppTheme.colors.brandPrimary else AppTheme.colors.cardBorder
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Card Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppTheme.colors.statusInfoBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Mihomo",
                                tint = AppTheme.colors.statusInfoText,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Mihomo",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isRunning) AppTheme.colors.statusSuccessBg else AppTheme.colors.surfaceVariant
                                ) {
                                    Text(
                                        text = if (isRunning) "运行中" else "已停止",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRunning) AppTheme.colors.statusSuccessText else AppTheme.colors.textMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "点击进入 Mihomo 控制台",
                                fontSize = 12.5.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        Switch(
                            checked = isRunning,
                            onCheckedChange = { onToggleVpn() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppTheme.colors.brandPrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = AppTheme.colors.innerBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2x2 Status Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewMetricCell(
                            modifier = Modifier.weight(1f),
                            label = "当前代理节点",
                            value = currentNodeName,
                            badge = currentNodeLatency?.let { "${it}ms" },
                            highlight = isRunning
                        )
                        OverviewMetricCell(
                            modifier = Modifier.weight(1f),
                            label = "当前激活配置",
                            value = activeProfileName,
                            highlight = false
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewMetricCell(
                            modifier = Modifier.weight(1f),
                            label = "出站分流模式",
                            value = proxyModeText,
                            highlight = false
                        )
                        OverviewMetricCell(
                            modifier = Modifier.weight(1f),
                            label = "覆写与 Meta 特性",
                            value = buildString {
                                append(if (overrideSettings.enableOverride) "覆写开启" else "覆写关闭")
                                append(" · ")
                                append(if (metaSettings.enableMetaFeatures && metaSettings.snifferEnable) "嗅探开启" else "标准模式")
                            },
                            highlight = false
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = AppTheme.colors.divider, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Shortcut Pills Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickJumpPill(
                                icon = Icons.Default.Speed,
                                text = "节点选择",
                                onClick = onNavigateToProxy
                            )
                            QuickJumpPill(
                                icon = Icons.Default.Description,
                                text = "配置管理",
                                onClick = onNavigateToProfiles
                            )
                            QuickJumpPill(
                                icon = Icons.Default.Tune,
                                text = "覆写设置",
                                onClick = onNavigateToOverride
                            )
                            QuickJumpPill(
                                icon = Icons.Default.Settings,
                                text = "设置",
                                onClick = onNavigateToSettings
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Large Card 2: EasyTier Virtual Mesh Networking Overview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNavigateToEasyTier()
                    },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(
                    1.dp,
                    if (easyTierRuntime.isRunning) AppTheme.colors.statusSuccessBorder else AppTheme.colors.cardBorder
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Card Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppTheme.colors.statusSuccessBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "EasyTier",
                                tint = AppTheme.colors.statusSuccessText,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        val easyTierDisplayChecked = easyTierTargetRunning ?: easyTierRuntime.isRunning
                        val easyTierStatusText = when {
                            isEasyTierTransitioning && easyTierTargetRunning == true -> "启动中..."
                            isEasyTierTransitioning && easyTierTargetRunning == false -> "停止中..."
                            easyTierRuntime.isRunning -> "组网运行中"
                            else -> "未连接"
                        }
                        val easyTierBadgeActive = if (isEasyTierTransitioning) {
                            easyTierTargetRunning == true
                        } else {
                            easyTierRuntime.isRunning
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "EasyTier",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (easyTierBadgeActive) AppTheme.colors.statusSuccessBg else AppTheme.colors.surfaceVariant
                                ) {
                                    Text(
                                        text = easyTierStatusText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (easyTierBadgeActive) AppTheme.colors.statusSuccessText else AppTheme.colors.textMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (easyTierRuntime.isRunning) {
                                    "当前实例: ${easyTierRuntime.runningProfileName}"
                                } else {
                                    "点击进入 EasyTier 控制台"
                                },
                                fontSize = 12.5.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        Switch(
                            checked = easyTierDisplayChecked,
                            onCheckedChange = {
                                if (!isEasyTierTransitioning) {
                                    onToggleEasyTier()
                                }
                            },
                            thumbContent = if (isEasyTierTransitioning) {
                                {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = if (easyTierDisplayChecked) AppTheme.colors.statusSuccessText else AppTheme.colors.textMuted
                                    )
                                }
                            } else {
                                null
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppTheme.colors.brandPrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = AppTheme.colors.innerBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2x2 Status Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewMetricCell(
                            modifier = Modifier.weight(1f),
                            label = "虚拟内网 IPv4",
                            value = if (easyTierRuntime.isRunning) {
                                easyTierRuntime.allocatedIpv4
                            } else if (currentEasyTierProfile?.dhcp == true) {
                                "DHCP 自动分配"
                            } else {
                                "${currentEasyTierProfile?.virtualIpv4 ?: "10.144.144.1"}/${currentEasyTierProfile?.networkLength ?: 24}"
                            },
                            highlight = easyTierRuntime.isRunning
                        )
                        OverviewMetricCell(
                            modifier = Modifier.weight(1f),
                            label = "网络名称 (Network)",
                            value = if (easyTierRuntime.isRunning) {
                                easyTierRuntime.networkName
                            } else {
                                currentEasyTierProfile?.networkName?.ifBlank { "easytier" } ?: "easytier"
                            },
                            highlight = false
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewMetricCell(
                            modifier = Modifier.weight(1f),
                            label = "对端节点 (Peers)",
                            value = if (easyTierRuntime.isRunning) {
                                "${easyTierRuntime.peers.size} 个连接对端"
                            } else {
                                currentEasyTierProfile?.networkingMethod?.label ?: "手动指定"
                            },
                            highlight = false
                        )
                        OverviewMetricCell(
                            modifier = Modifier.weight(1f),
                            label = if (easyTierRuntime.isRunning) "组网实时速率" else "双核桥接协议",
                            value = if (easyTierRuntime.isRunning) {
                                "↓ ${formatSpeed(easyTierRuntime.rxSpeed)} ↑ ${formatSpeed(easyTierRuntime.txSpeed)}"
                            } else if (currentEasyTierProfile?.socks5Enabled == true) {
                                "SOCKS5 :${currentEasyTierProfile.socks5Port}"
                            } else {
                                "本地直连模式"
                            },
                            highlight = easyTierRuntime.isRunning && (easyTierRuntime.rxSpeed > 0L || easyTierRuntime.txSpeed > 0L)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Info banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = null,
                            tint = AppTheme.colors.statusSuccessText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "跟Mihomo同时开启时Easytier将作为其中一个节点",
                            fontSize = 11.5.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TopologyNodeChip(
    label: String,
    active: Boolean,
    activeBg: Color,
    activeText: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) activeBg else AppTheme.colors.surfaceSubtle
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) activeText else AppTheme.colors.textMuted,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun OverviewMetricCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    badge: String? = null,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) AppTheme.colors.statusInfoBg.copy(alpha = if (AppTheme.colors.isDark) 0.35f else 0.7f) else AppTheme.colors.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = AppTheme.colors.textSecondary
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppTheme.colors.statusSuccessBg
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.statusSuccessText,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) AppTheme.colors.statusInfoText else AppTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickJumpPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppTheme.colors.surfaceVariant,
        border = BorderStroke(1.dp, AppTheme.colors.innerBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = AppTheme.colors.textPrimary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = AppTheme.colors.textPrimary
            )
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0.0 KB/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1000) {
        return String.format(Locale.US, "%.1f KB/s", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB/s", mb)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
