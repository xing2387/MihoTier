package top.xing2387.mihotier.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.xing2387.mihotier.core.config.EasyTierRepository
import top.xing2387.mihotier.core.config.MetaFeaturesSettingsRepository
import top.xing2387.mihotier.core.config.OverrideSettingsRepository
import top.xing2387.mihotier.core.config.ProfileRepository
import top.xing2387.mihotier.core.vpn.UnifiedVpnService
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
                                    top.xing2387.mihotier.core.config.ConfigManager.getConfiguredClashMode(context) == "global"
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

    val upSpeedBytes = if (isRunning) trafficStats.upSpeed else 0L
    val downSpeedBytes = if (isRunning) trafficStats.downSpeed else 0L
    val totalUpBytes = if (isRunning) trafficStats.upTotal else 0L
    val totalDownBytes = if (isRunning) trafficStats.downTotal else 0L

    Scaffold(
        containerColor = Color(0xFFEEF3F8)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // 1. Top Brand & Global Tunnel Status Header
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
                            .background(Color(0xFF104179)),
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
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Mihomo × EasyTier 双核状态总览",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Tunnel status pill
                val anyTunActive = isRunning || easyTierRuntime.isRunning
                val tunStatusLabel = when {
                    isRunning && easyTierRuntime.isRunning -> "双核 TUN 运行中"
                    isRunning -> "TUN 隧道运行中"
                    easyTierRuntime.isRunning -> "EasyTier TUN 接管"
                    else -> "TUN 待机中"
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (anyTunActive) Color(0xFFD1FAE5) else Color(0xFFE2E8F0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (anyTunActive) Color(0xFF059669) else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tunStatusLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (anyTunActive) Color(0xFF065F46) else Color(0xFF475569)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Dual-Core Bridge & Real-time Traffic Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Topology Strip: Android TUN ⇄ Mihomo ⇄ EasyTier
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TopologyNodeChip(
                            label = "Android TUN",
                            active = isRunning || easyTierRuntime.isRunning,
                            activeBg = Color(0xFFBAE6FD),
                            activeText = Color(0xFF0369A1)
                        )
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = if (isRunning || easyTierRuntime.isRunning) Color(0xFF0369A1) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        TopologyNodeChip(
                            label = "Mihomo 核心",
                            active = isRunning,
                            activeBg = Color(0xFFD1FAE5),
                            activeText = Color(0xFF065F46)
                        )
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = if (easyTierRuntime.isRunning) Color(0xFF065F46) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        TopologyNodeChip(
                            label = "EasyTier 组网",
                            active = easyTierRuntime.isRunning,
                            activeBg = Color(0xFFD1FAE5),
                            activeText = Color(0xFF065F46)
                        )
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
                                .background(Color(0xFFF0F9FF))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFBAE6FD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Download",
                                    tint = Color(0xFF0369A1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "累计 ${formatBytes(totalDownBytes)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = formatSpeed(downSpeedBytes),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        // Upload Speed Box
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF0FDF4))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFA7F3D0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Upload",
                                    tint = Color(0xFF065F46),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "累计 ${formatBytes(totalUpBytes)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = formatSpeed(upSpeedBytes),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF0F172A)
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
                color = Color(0xFF475569),
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            // 3. Large Card 1: Mihomo (Clash.Meta) Overview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToClash() },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(
                    width = if (isRunning) 1.5.dp else 1.dp,
                    color = if (isRunning) Color(0xFF5B8DEF) else Color(0xFFE2E8F0)
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
                                .background(Color(0xFFBAE6FD)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Mihomo",
                                tint = Color(0xFF0369A1),
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
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isRunning) Color(0xFFD1FAE5) else Color(0xFFF1F5F9)
                                ) {
                                    Text(
                                        text = if (isRunning) "运行中" else "已停止",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRunning) Color(0xFF065F46) else Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "点击进入 Mihomo 控制台",
                                fontSize = 12.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Switch(
                            checked = isRunning,
                            onCheckedChange = { onToggleVpn() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1D68BD),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFCBD5E1)
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
                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
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

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Enter Mihomo",
                            tint = Color(0xFF0369A1),
                            modifier = Modifier.size(14.dp)
                        )
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(
                    1.dp,
                    if (easyTierRuntime.isRunning) Color(0xFF6EE7B7) else Color(0xFFE2E8F0)
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
                                .background(Color(0xFFA7F3D0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "EasyTier",
                                tint = Color(0xFF065F46),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "EasyTier",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (easyTierRuntime.isRunning) Color(0xFFD1FAE5) else Color(0xFFF1F5F9)
                                ) {
                                    Text(
                                        text = if (easyTierRuntime.isRunning) "组网运行中" else "未连接",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (easyTierRuntime.isRunning) Color(0xFF065F46) else Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (easyTierRuntime.isRunning) {
                                    "当前实例: ${easyTierRuntime.runningProfileName}"
                                } else {
                                    "无 TUN 桥接引擎 · 实例: ${currentEasyTierProfile?.instanceName ?: "easytier"}"
                                },
                                fontSize = 12.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "进入",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF065F46)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = Color(0xFF065F46),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
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
                            label = "双核桥接协议",
                            value = if (currentEasyTierProfile?.socks5Enabled == true) {
                                "SOCKS5 :${currentEasyTierProfile.socks5Port}"
                            } else {
                                "本地直连模式"
                            },
                            highlight = false
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Info banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = null,
                            tint = Color(0xFF065F46),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "组网流量可通过 Mihomo 自定义规则按内网网段自动分流转发",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
        color = if (active) activeBg else Color(0xFFE2E8F0)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) activeText else Color(0xFF64748B),
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
            .background(if (highlight) Color(0xFFF0F9FF) else Color(0xFFF8FAFC))
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
                color = Color(0xFF64748B)
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFD1FAE5)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46),
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
            color = if (highlight) Color(0xFF0369A1) else Color(0xFF0F172A),
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
        color = Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color(0xFF334155),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155)
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
