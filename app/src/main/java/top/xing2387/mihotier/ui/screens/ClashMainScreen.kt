package top.xing2387.mihotier.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import top.xing2387.mihotier.R
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.ProfileRepository
import top.xing2387.mihotier.core.vpn.UnifiedVpnService
import java.util.concurrent.TimeUnit

@Composable
fun ClashMainScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProxy: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    isRunning: Boolean = false,
    trafficStats: UnifiedVpnService.TrafficStats = UnifiedVpnService.TrafficStats(),
    onToggleVpn: () -> Unit = {},
    onRestartVpn: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Sensible defaults & active profile state
    val profiles by ProfileRepository.profiles.collectAsState()
    val activeProfile = remember(profiles) {
        ProfileRepository.getActiveProfile() ?: profiles.firstOrNull()
    }
    val activeProfileDisplayName = remember(activeProfile) {
        if (activeProfile != null) {
            val n = activeProfile.name
            if (n.endsWith(".yaml") || n.endsWith(".yml")) n else "$n.yaml"
        } else {
            "未选择配置文件"
        }
    }

    // 2. Sensible defaults for Proxy Mode & Current Node (NO hardcoded dummy values)
    var rawProxyMode by remember { mutableStateOf("rule") }
    var proxyModeText by remember { mutableStateOf("规则分流") }
    var currentNodeName by remember { mutableStateOf(if (isRunning) "获取中..." else "未连接") }
    var currentNodeLatency by remember { mutableStateOf<Int?>(null) }

    // Dialog States
    var showModeDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isUpdatingProfile by remember { mutableStateOf(false) }

    // Adaptive duration formatting: hide 0 units, minimum unit minutes
    fun formatVpnDuration(totalSeconds: Long): String {
        val totalMinutes = totalSeconds / 60
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}天")
        if (hours > 0) parts.add("${hours}小时")
        if (minutes > 0) parts.add("${minutes}分钟")

        return if (parts.isEmpty()) {
            "< 1分钟"
        } else {
            parts.joinToString(" ")
        }
    }

    var isTestingLatency by remember { mutableStateOf(false) }

    fun testCurrentNodeLatency(nodeName: String) {
        if (isTestingLatency || nodeName.isBlank() || nodeName == "未连接") return
        isTestingLatency = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val encodedName = java.net.URLEncoder.encode(nodeName, "UTF-8")
                val url = "http://127.0.0.1:9090/proxies/$encodedName/delay?timeout=4000&url=http://www.gstatic.com/generate_204"
                val req = Request.Builder().url(url).get().build()
                val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val d = JSONObject(body).optInt("delay", 0)
                        if (d > 0) {
                            withContext(Dispatchers.Main) {
                                currentNodeLatency = d
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                withContext(Dispatchers.Main) {
                    isTestingLatency = false
                }
            }
        }
    }

    // Duration timer (starts from 0s when connected, resets on stop)
    var durationSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                durationSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        } else {
            durationSeconds = 0L
            currentNodeName = "未连接"
            currentNodeLatency = null
            isTestingLatency = false
        }
    }

    // Real-time instantaneous speed (bytes per second) directly from Mihomo core
    val upSpeedBytes = if (isRunning) trafficStats.upSpeed else 0L
    val downSpeedBytes = if (isRunning) trafficStats.downSpeed else 0L

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0.0 KB/s"
        val kb = bytesPerSec / 1024.0
        if (kb < 1000) {
            return String.format(java.util.Locale.US, "%.1f KB/s", kb)
        }
        val mb = kb / 1024.0
        return String.format(java.util.Locale.US, "%.1f MB/s", mb)
    }

    // Wave Chart Points:
    // "上半部分的图表，不要记录历史，app回到前台才从0开始接收并展示数据"
    val trafficPoints = remember { mutableStateListOf<Float>() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Clear any stored history upon returning to foreground, start from 0
                trafficPoints.clear()
                repeat(24) { trafficPoints.add(0f) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Push new point on every 1-second clock tick
    LaunchedEffect(trafficStats.tick, isRunning) {
        if (trafficPoints.isEmpty()) {
            repeat(24) { trafficPoints.add(0f) }
        }
        val currentSpeedBytes = if (isRunning) {
            (downSpeedBytes + upSpeedBytes).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }
        if (trafficPoints.size >= 32) {
            trafficPoints.removeAt(0)
        }
        trafficPoints.add(currentSpeedBytes)
    }

    // Periodically query Mihomo core status (Mode & Selected Proxy) when running
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()

            var lastTestedNode = ""
            while (isActive) {
                withContext(Dispatchers.IO) {
                    try {
                        // 1. Query mode
                        val configReq = Request.Builder().url("http://127.0.0.1:9090/configs").get().build()
                        httpClient.newCall(configReq).execute().use { res ->
                            if (res.isSuccessful) {
                                val json = JSONObject(res.body?.string() ?: "")
                                val rawM = json.optString("mode", "rule")
                                val m = if (top.xing2387.mihotier.core.config.EasyTierRepository.getBridgeRoutingInfo(context).enabled &&
                                    ConfigManager.getConfiguredClashMode(context) == "global"
                                ) {
                                    "global"
                                } else {
                                    rawM
                                }
                                rawProxyMode = m
                                proxyModeText = when (m.lowercase()) {
                                    "global" -> "全局代理"
                                    "direct" -> "直连模式"
                                    else -> "规则分流"
                                }
                            }
                        }

                        // 2. Query proxies
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
                                        var delay = 0
                                        if (hist != null && hist.length() > 0) {
                                            val lastH = hist.getJSONObject(hist.length() - 1)
                                            delay = lastH.optInt("delay", 0)
                                        }
                                        if (delay > 0) {
                                            currentNodeLatency = delay
                                        }
                                        // Auto test latency on connect or when node changes
                                        if (active != lastTestedNode || currentNodeLatency == null) {
                                            lastTestedNode = active
                                            testCurrentNodeLatency(active)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore API error while starting up
                    }
                }
                delay(3000)
            }
        } else {
            currentNodeLatency = null
            currentNodeName = "未连接"
            isTestingLatency = false
        }
    }

    fun updateProxyMode(newMode: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val currentOverrides = top.xing2387.mihotier.core.config.OverrideSettingsRepository.load(context)
                top.xing2387.mihotier.core.config.OverrideSettingsRepository.save(
                    context,
                    currentOverrides.copy(enableOverride = true, mode = newMode.lowercase())
                )
                rawProxyMode = newMode
                proxyModeText = when (newMode.lowercase()) {
                    "global" -> "全局代理"
                    "direct" -> "直连模式"
                    else -> "规则分流"
                }
                if (top.xing2387.mihotier.core.config.EasyTierRepository.getBridgeRoutingInfo(context).enabled && isRunning) {
                    withContext(Dispatchers.Main) {
                        onRestartVpn()
                        Toast.makeText(context, "模式已切换为 $proxyModeText", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val httpClient = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).build()
                    val body = "{\"mode\":\"$newMode\"}".toRequestBody("application/json".toMediaType())
                    val req = Request.Builder().url("http://127.0.0.1:9090/configs").patch(body).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "模式已切换为 $proxyModeText", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "切换模式失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateCurrentProfile() {
        val prof = activeProfile
        if (prof == null || prof.url.isBlank()) {
            Toast.makeText(context, "本地配置无需更新", Toast.LENGTH_SHORT).show()
            return
        }
        isUpdatingProfile = true
        coroutineScope.launch {
            val result = ConfigManager.downloadAndSaveConfig(context, prof.url, prof.id)
            isUpdatingProfile = false
            if (result.isSuccess) {
                Toast.makeText(context, "配置文件已更新", Toast.LENGTH_SHORT).show()
                if (isRunning) {
                    onRestartVpn()
                }
            } else {
                Toast.makeText(context, "更新失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Page background
    val pageBgColor = Color(0xFFEEF3F8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 32.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Clash Meta",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "for Android",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Normal
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Bolt / Speed test icon
                    IconButton(
                        onClick = {
                            if (isRunning) {
                                Toast.makeText(context, "正在测试节点延迟...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "代理未开启", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bolt),
                            contentDescription = "Speed Test",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Refresh icon
                    IconButton(
                        onClick = {
                            if (isRunning) {
                                onRestartVpn()
                                Toast.makeText(context, "正在重启代理核心...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "代理未开启", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Status & Traffic Hero Card (Unified Deep Navy Blue Card matching Figure 1)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF104179)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Top Row: Status Dot + Connection Text + Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRunning) Color(0xFF22C55E) else Color(0xFF94A3B8)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRunning) "已连接 · ${formatVpnDuration(durationSeconds)}" else "未连接",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Switch(
                            checked = isRunning,
                            onCheckedChange = { onToggleVpn() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF5B7EAC),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF1E3A5F)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Middle Row: Speeds (Download & Upload)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Download Speed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "↓",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF22C55E)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatSpeed(downSpeedBytes),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Upload Speed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "↑",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatSpeed(upSpeedBytes),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Wave Chart with Y-Axis directly in the blue card
                    TrafficWaveChart(
                        points = trafficPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val disabledCardBg = Color(0xFFE2E8F0)
            val enabledCardBg = Color.White

            val isModeClickable = isRunning
            val isNodeClickable = isRunning

            // 2. Middle 2-Column Cards (代理模式 & 当前节点)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Card: 代理模式
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(98.dp)
                        .clickable {
                            if (isModeClickable) {
                                showModeDialog = true
                            } else {
                                Toast.makeText(context, "请先开启代理", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isModeClickable) enabledCardBg else disabledCardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Proxy Mode",
                            tint = if (isModeClickable) Color(0xFF0F172A) else Color(0xFF94A3B8),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "代理模式",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isModeClickable) Color(0xFF0F172A) else Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = proxyModeText,
                                fontSize = 13.sp,
                                color = if (isModeClickable) Color(0xFF64748B) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Right Card: 当前节点
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(98.dp)
                        .clickable {
                            if (isNodeClickable) {
                                onNavigateToProxy()
                            } else {
                                Toast.makeText(context, "请先开启代理", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isNodeClickable) enabledCardBg else disabledCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Node",
                                tint = if (isNodeClickable) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "当前节点",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNodeClickable) Color(0xFF0F172A) else Color(0xFF94A3B8)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = currentNodeName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNodeClickable) Color(0xFF0F172A) else Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !isNodeClickable -> Color(0xFF94A3B8)
                                            currentNodeLatency != null && currentNodeLatency!! > 0 -> Color(0xFF22C55E)
                                            isTestingLatency -> Color(0xFF38BDF8)
                                            else -> Color(0xFF94A3B8)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    !isNodeClickable -> "N/A"
                                    isTestingLatency -> "测速中..."
                                    currentNodeLatency != null && currentNodeLatency!! > 0 -> "${currentNodeLatency}ms"
                                    else -> "N/A"
                                },
                                fontSize = 12.sp,
                                color = when {
                                    !isNodeClickable -> Color(0xFF94A3B8)
                                    currentNodeLatency != null && currentNodeLatency!! > 0 -> Color(0xFF22C55E)
                                    isTestingLatency -> Color(0xFF38BDF8)
                                    else -> Color(0xFF64748B)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. 配置文件 (Profile) Card with distinct white background & pill capsule button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProfiles() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = enabledCardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Profile",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "配置文件 (Profile)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (activeProfile != null) "$activeProfileDisplayName (已激活)" else activeProfileDisplayName,
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Pill capsule button: [ 更新 🔄 ]
                    Surface(
                        modifier = Modifier.clickable { updateCurrentProfile() },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        color = Color(0xFFF8FAFC)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUpdatingProfile) "更新中" else "更新",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Update",
                                tint = Color(0xFF334155),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Bottom Menu Group Card (White Group Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    HomeMenuItem(
                        icon = Icons.Default.Terminal,
                        iconBgColor = Color(0xFFA7F3D0), // Light mint green
                        iconTintColor = Color(0xFF065F46),
                        title = "实时日志 (Logs)",
                        subtitle = "查看运行日志",
                        onClick = onNavigateToLogs
                    )

                    HomeMenuItem(
                        icon = Icons.Default.Settings,
                        iconBgColor = Color(0xFFBAE6FD), // Sky blue
                        iconTintColor = Color(0xFF0369A1),
                        title = "设置 (Settings)",
                        subtitle = "自定义代理与应用行为",
                        onClick = onNavigateToSettings
                    )

                    HomeMenuItem(
                        icon = Icons.Default.HelpOutline,
                        iconBgColor = Color(0xFFFED7AA), // Amber
                        iconTintColor = Color(0xFFB45309),
                        title = "帮助文档 (Help)",
                        subtitle = "使用指南与常见问题",
                        onClick = { showHelpDialog = true }
                    )

                    HomeMenuItem(
                        icon = Icons.Default.Info,
                        iconBgColor = Color(0xFFE9D5FF), // Lavender/purple
                        iconTintColor = Color(0xFF7E22CE),
                        title = "关于软件 (About)",
                        subtitle = "版本 v1.18.0 - Clash Meta",
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    // Switch Mode Dialog
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("切换代理模式", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        "rule" to "规则分流 (Rule)",
                        "global" to "全局代理 (Global)",
                        "direct" to "直连模式 (Direct)"
                    ).forEach { (modeVal, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    updateProxyMode(modeVal)
                                    showModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (rawProxyMode.lowercase() == modeVal.lowercase()),
                                onClick = {
                                    updateProxyMode(modeVal)
                                    showModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("帮助文档 (Help)", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "1. 如何开启代理？\n点击顶部的运行开关即可建立本地 VPN 隧道。\n\n" +
                               "2. 如何切换代理模式？\n点击首页的【代理模式】卡片即可快速切换规则、全局或直连模式。\n\n" +
                               "3. 如何选择节点？\n点击【当前节点】卡片即可进入节点列表进行测速和选择。\n\n" +
                               "4. 如何添加配置？\n点击【配置文件】卡片进入配置管理，可下载订阅链接或导入本地 yaml 文件。",
                        fontSize = 14.sp,
                        color = Color(0xFF334155),
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("我知道了")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于软件", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Clash Meta for Android\n版本：v1.18.0 - Clash Meta\n基于 Mihomo (Clash.Meta) 与 EasyTier 核心驱动。\n提供快速、安全、稳定的全平台网络互联体验。",
                        fontSize = 14.sp,
                        color = Color(0xFF334155),
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun HomeMenuItem(
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTintColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Navigate",
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
fun TrafficWaveChart(
    points: List<Float>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelTextStyle = TextStyle(
        color = Color.White.copy(alpha = 0.65f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal
    )

    // Calculate dynamic ceiling based on current points
    val maxData = points.maxOrNull() ?: 0f
    val kb = 1024f
    val mb = 1024f * 1024f
    val ladder = listOf(
        100f * kb, 200f * kb, 500f * kb,
        1f * mb, 2f * mb, 5f * mb, 10f * mb, 20f * mb, 50f * mb, 100f * mb, 200f * mb
    )
    val target = if (maxData > 0f) maxData * 1.15f else 0f
    var ceiling = ladder[0]
    for (step in ladder) {
        if (step >= target) {
            ceiling = step
            break
        }
        ceiling = step
    }

    fun formatYAxis(b: Float): String {
        if (b <= 0f) return "0"
        if (b >= mb) {
            val v = b / mb
            return if (v % 1f == 0f) "${v.toInt()} MB/s" else String.format(java.util.Locale.US, "%.1f MB/s", v)
        }
        if (b >= kb) {
            val v = b / kb
            return if (v % 1f == 0f) "${v.toInt()} KB/s" else String.format(java.util.Locale.US, "%.1f KB/s", v)
        }
        return "${b.toInt()} B/s"
    }

    val topLabel = formatYAxis(ceiling)
    val midLabel = formatYAxis(ceiling / 2f)
    val botLabel = "0"

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0 || points.isEmpty()) return@Canvas

        // Measure Y-axis labels
        val topMeasured = textMeasurer.measure(topLabel, style = labelTextStyle)
        val midMeasured = textMeasurer.measure(midLabel, style = labelTextStyle)
        val botMeasured = textMeasurer.measure(botLabel, style = labelTextStyle)

        val maxTextWidth = maxOf(topMeasured.size.width, midMeasured.size.width, botMeasured.size.width).toFloat()
        val labelGap = 10.dp.toPx()
        val chartStartX = maxTextWidth + labelGap
        val chartEndX = width
        val chartWidth = chartEndX - chartStartX

        val topY = topMeasured.size.height / 2f + 2.dp.toPx()
        val botY = height - (botMeasured.size.height / 2f + 2.dp.toPx())
        val midY = (topY + botY) / 2f

        // Draw Y-axis labels (right aligned before the dashed lines)
        drawText(
            textLayoutResult = topMeasured,
            topLeft = Offset(chartStartX - labelGap - topMeasured.size.width, topY - topMeasured.size.height / 2f)
        )
        drawText(
            textLayoutResult = midMeasured,
            topLeft = Offset(chartStartX - labelGap - midMeasured.size.width, midY - midMeasured.size.height / 2f)
        )
        drawText(
            textLayoutResult = botMeasured,
            topLeft = Offset(chartStartX - labelGap - botMeasured.size.width, botY - botMeasured.size.height / 2f)
        )

        // Draw 3 horizontal dashed grid lines
        val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
        val gridColor = Color.White.copy(alpha = 0.22f)
        val gridWidth = 1.dp.toPx()

        listOf(topY, midY, botY).forEach { lineY ->
            drawLine(
                color = gridColor,
                start = Offset(chartStartX, lineY),
                end = Offset(chartEndX, lineY),
                strokeWidth = gridWidth,
                pathEffect = dashPathEffect
            )
        }

        // Compute Wave coordinates
        val isAllZero = points.all { it == 0f }
        val availableHeight = botY - topY
        val stepX = if (points.size > 1) chartWidth / (points.size - 1) else chartWidth

        val coordinates = points.mapIndexed { index, value ->
            val x = chartStartX + index * stepX
            val subtleWave = if (isAllZero) {
                (kotlin.math.sin(index * 0.7).toFloat() * 2.5.dp.toPx())
            } else 0f
            val normalizedY = if (isAllZero) {
                botY - 3.dp.toPx() + subtleWave
            } else {
                botY - (value / ceiling).coerceIn(0f, 1f) * availableHeight
            }
            Offset(x, normalizedY.coerceIn(topY, botY + 2.dp.toPx()))
        }

        if (coordinates.isNotEmpty()) {
            val strokePath = Path()
            val fillPath = Path()

            strokePath.moveTo(coordinates[0].x, coordinates[0].y)
            fillPath.moveTo(coordinates[0].x, botY)
            fillPath.lineTo(coordinates[0].x, coordinates[0].y)

            for (i in 0 until coordinates.size - 1) {
                val p0 = coordinates[i]
                val p1 = coordinates[i + 1]
                val midX = (p0.x + p1.x) / 2
                strokePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }

            fillPath.lineTo(coordinates.last().x, botY)
            fillPath.close()

            // Translucent cyan/blue gradient fill below curve
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF60A5FA).copy(alpha = 0.35f),
                        Color(0xFF38BDF8).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    startY = topY,
                    endY = botY
                )
            )

            // Cyan curve line on top
            drawPath(
                path = strokePath,
                color = Color(0xFF60A5FA),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
