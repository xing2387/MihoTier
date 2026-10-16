package top.xing2387.mihotier.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import top.xing2387.mihotier.core.config.BandwidthFormatter
import top.xing2387.mihotier.ui.theme.AppTheme
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ConnectionColumn(
    val key: String,
    val titleEn: String,
    val titleZh: String,
    val width: Dp
) {
    HOST("host", "Host", "域名/主机", 190.dp),
    DOWNLOADED("downloaded", "Downloaded", "下载量", 86.dp),
    UPLOADED("uploaded", "Uploaded", "上传量", 86.dp),
    DL_SPEED("dl_speed", "DL Speed", "下载速度", 92.dp),
    UL_SPEED("ul_speed", "UL Speed", "上传速度", 92.dp),
    CHAINS("chains", "Chains", "节点链", 180.dp),
    RULE("rule", "Rule", "命中规则", 170.dp),
    PROCESS("process", "Process", "进程/应用", 130.dp),
    TIME("time", "Time", "连接时间", 100.dp),
    SOURCE("source", "Source", "源地址", 135.dp),
    DESTINATION("destination", "Destination", "目标地址", 160.dp),
    TYPE("type", "Type", "连接类型", 90.dp)
}

data class ClashConnectionItem(
    val id: String,
    val host: String,
    val download: Long,
    val upload: Long,
    val dlSpeed: Long,
    val ulSpeed: Long,
    val chains: String,
    val rule: String,
    val process: String,
    val startEpochMs: Long,
    val startRaw: String,
    val source: String,
    val destination: String,
    val type: String,
    val isClosed: Boolean = false
)

private const val PREFS_CONNECTIONS = "clash_connections_ui_prefs"
private const val KEY_VISIBLE_COLUMNS = "visible_columns"
private const val KEY_TABLE_MODE = "table_mode"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashConnectionsScreen(
    isRunning: Boolean = false,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS_CONNECTIONS, Context.MODE_PRIVATE) }

    val visibleColumns = remember {
        val saved = prefs.getString(KEY_VISIBLE_COLUMNS, null)
        val initial = if (saved.isNullOrBlank()) {
            ConnectionColumn.entries.toSet()
        } else {
            val keys = saved.split(",").toSet()
            ConnectionColumn.entries.filter { it.key in keys }.toSet().ifEmpty {
                ConnectionColumn.entries.toSet()
            }
        }
        mutableStateListOf<ConnectionColumn>().apply {
            addAll(ConnectionColumn.entries.filter { it in initial })
        }
    }

    fun saveVisibleColumns() {
        prefs.edit()
            .putString(KEY_VISIBLE_COLUMNS, visibleColumns.joinToString(",") { it.key })
            .apply()
    }

    var isTableMode by remember {
        mutableStateOf(prefs.getBoolean(KEY_TABLE_MODE, true))
    }

    var activeTab by remember { mutableStateOf(0) } // 0 = ACTIVE, 1 = CLOSED
    var filterQuery by remember { mutableStateOf("") }
    var isPaused by remember { mutableStateOf(false) }
    var showColumnsDialog by remember { mutableStateOf(false) }
    var selectedConnection by remember { mutableStateOf<ClashConnectionItem?>(null) }

    var sortColumn by remember { mutableStateOf(ConnectionColumn.TIME) }
    var sortAscending by remember { mutableStateOf(false) }

    var downloadTotal by remember { mutableLongStateOf(0L) }
    var uploadTotal by remember { mutableLongStateOf(0L) }

    val activeConnections = remember { mutableStateListOf<ClashConnectionItem>() }
    val closedConnections = remember { mutableStateListOf<ClashConnectionItem>() }
    val appLabelCache = remember { mutableStateMapOf<String, String>() }

    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    fun resolveProcessLabel(rawProcess: String): String {
        val clean = rawProcess.trim().substringAfterLast("/")
        if (clean.isEmpty()) return ""
        appLabelCache[clean]?.let { return it }
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(clean, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val formatted = if (label.isNotBlank() && label != clean) "$label ($clean)" else clean
            appLabelCache[clean] = formatted
            formatted
        } catch (_: Exception) {
            appLabelCache[clean] = clean
            clean
        }
    }

    fun closeConnectionById(id: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("http://127.0.0.1:9090/connections/$id")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
                withContext(Dispatchers.Main) {
                    val removed = activeConnections.find { it.id == id }
                    if (removed != null) {
                        activeConnections.remove(removed)
                        closedConnections.add(0, removed.copy(dlSpeed = 0L, ulSpeed = 0L, isClosed = true))
                    }
                    Toast.makeText(context, "已断开连接", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "断开连接失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun closeAllConnections() {
        if (activeTab == 1) {
            closedConnections.clear()
            Toast.makeText(context, "已清空历史连接记录", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("http://127.0.0.1:9090/connections")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
                withContext(Dispatchers.Main) {
                    val nowClosed = activeConnections.map {
                        it.copy(dlSpeed = 0L, ulSpeed = 0L, isClosed = true)
                    }
                    closedConnections.addAll(0, nowClosed)
                    while (closedConnections.size > 300) {
                        closedConnections.removeAt(closedConnections.lastIndex)
                    }
                    activeConnections.clear()
                    Toast.makeText(context, "已关闭全部活动连接", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Poll /connections every 1 second
    LaunchedEffect(isRunning, isPaused) {
        if (!isRunning) {
            activeConnections.clear()
            return@LaunchedEffect
        }
        var prevMap = mapOf<String, ClashConnectionItem>()
        var lastSampleTimeMs = 0L

        while (isActive && isRunning) {
            if (!isPaused) {
                withContext(Dispatchers.IO) {
                    try {
                        val req = Request.Builder()
                            .url("http://127.0.0.1:9090/connections")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            if (!res.isSuccessful) return@use
                            val bodyStr = res.body?.string() ?: return@use
                            val root = JSONObject(bodyStr)
                            val dlTotal = root.optLong("downloadTotal", 0L)
                            val ulTotal = root.optLong("uploadTotal", 0L)
                            val connArr = root.optJSONArray("connections")

                            val nowMs = System.currentTimeMillis()
                            val elapsedMs = if (lastSampleTimeMs > 0L) {
                                (nowMs - lastSampleTimeMs).coerceAtLeast(200L)
                            } else {
                                1000L
                            }

                            val newActiveList = mutableListOf<ClashConnectionItem>()
                            val newMap = LinkedHashMap<String, ClashConnectionItem>()

                            if (connArr != null) {
                                for (i in 0 until connArr.length()) {
                                    val cObj = connArr.optJSONObject(i) ?: continue
                                    val id = cObj.optString("id", "")
                                    if (id.isEmpty()) continue

                                    val meta = cObj.optJSONObject("metadata") ?: JSONObject()
                                    val network = meta.optString("network", "tcp")
                                    val type = meta.optString("type", "Tun")
                                    val srcIp = meta.optString("sourceIP", "")
                                    val srcPort = meta.optString("sourcePort", "")
                                    val dstIp = meta.optString("destinationIP", "")
                                    val dstPort = meta.optString("destinationPort", "")
                                    val remoteDst = meta.optString("remoteDestination", "")
                                    val rawHost = meta.optString("host", "")
                                    val rawProc = meta.optString("process", "").ifBlank {
                                        meta.optString("processPath", "")
                                    }

                                    val hostBase = rawHost.ifBlank { dstIp }.ifBlank { remoteDst }
                                    val hostDisplay = if (dstPort.isNotBlank() && !hostBase.endsWith(":$dstPort")) {
                                        "$hostBase:$dstPort"
                                    } else {
                                        hostBase
                                    }

                                    val dl = cObj.optLong("download", 0L)
                                    val ul = cObj.optLong("upload", 0L)
                                    val prevItem = prevMap[id]
                                    val dlSpd = if (prevItem != null && lastSampleTimeMs > 0L) {
                                        ((dl - prevItem.download).coerceAtLeast(0L) * 1000L) / elapsedMs
                                    } else 0L
                                    val ulSpd = if (prevItem != null && lastSampleTimeMs > 0L) {
                                        ((ul - prevItem.upload).coerceAtLeast(0L) * 1000L) / elapsedMs
                                    } else 0L

                                    val chainsArr = cObj.optJSONArray("chains")
                                    val chainList = mutableListOf<String>()
                                    if (chainsArr != null) {
                                        for (j in chainsArr.length() - 1 downTo 0) {
                                            val ch = chainsArr.optString(j)
                                            if (ch.isNotBlank()) chainList.add(ch)
                                        }
                                    }
                                    val chainsStr = chainList.joinToString(" / ").ifBlank { "DIRECT" }

                                    val ruleName = cObj.optString("rule", "Match")
                                    val rulePayload = cObj.optString("rulePayload", "")
                                    val ruleStr = if (rulePayload.isNotBlank()) {
                                        "$ruleName($rulePayload)"
                                    } else {
                                        ruleName
                                    }

                                    val startStr = cObj.optString("start", "")
                                    val startEpoch = parseIsoMillis(startStr)

                                    val sourceStr = if (srcPort.isNotBlank()) "$srcIp:$srcPort" else srcIp
                                    val dstBase = remoteDst.ifBlank { dstIp }.ifBlank { rawHost }
                                    val destinationStr = if (dstPort.isNotBlank() && dstBase.isNotBlank()) {
                                        "$dstBase:$dstPort"
                                    } else {
                                        dstBase
                                    }
                                    val typeStr = "$type($network)"
                                    val procLabel = resolveProcessLabel(rawProc)

                                    val item = ClashConnectionItem(
                                        id = id,
                                        host = hostDisplay,
                                        download = dl,
                                        upload = ul,
                                        dlSpeed = dlSpd,
                                        ulSpeed = ulSpd,
                                        chains = chainsStr,
                                        rule = ruleStr,
                                        process = procLabel,
                                        startEpochMs = startEpoch,
                                        startRaw = startStr,
                                        source = sourceStr,
                                        destination = destinationStr,
                                        type = typeStr,
                                        isClosed = false
                                    )
                                    newActiveList.add(item)
                                    newMap[id] = item
                                }
                            }

                            val newlyClosed = if (prevMap.isNotEmpty()) {
                                prevMap.values.filter { it.id !in newMap }.map {
                                    it.copy(dlSpeed = 0L, ulSpeed = 0L, isClosed = true)
                                }
                            } else {
                                emptyList()
                            }

                            prevMap = newMap
                            lastSampleTimeMs = nowMs

                            withContext(Dispatchers.Main) {
                                downloadTotal = dlTotal
                                uploadTotal = ulTotal
                                activeConnections.clear()
                                activeConnections.addAll(newActiveList)
                                if (newlyClosed.isNotEmpty()) {
                                    val existingClosedIds = closedConnections.asSequence().map { it.id }.toHashSet()
                                    val uniqueClosed = newlyClosed.filter { it.id !in existingClosedIds }
                                    if (uniqueClosed.isNotEmpty()) {
                                        closedConnections.addAll(0, uniqueClosed)
                                        while (closedConnections.size > 300) {
                                            closedConnections.removeAt(closedConnections.lastIndex)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            delay(1000L)
        }
    }

    val sourceList = if (activeTab == 0) activeConnections else closedConnections
    val filteredAndSorted = remember(
        sourceList.toList(),
        filterQuery,
        sortColumn,
        sortAscending
    ) {
        val q = filterQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            sourceList.toList()
        } else {
            sourceList.filter { item ->
                item.host.lowercase().contains(q) ||
                    item.chains.lowercase().contains(q) ||
                    item.rule.lowercase().contains(q) ||
                    item.process.lowercase().contains(q) ||
                    item.source.lowercase().contains(q) ||
                    item.destination.lowercase().contains(q) ||
                    item.type.lowercase().contains(q)
            }
        }
        val comparator = when (sortColumn) {
            ConnectionColumn.HOST -> compareBy<ClashConnectionItem> { it.host.lowercase() }
            ConnectionColumn.DOWNLOADED -> compareBy { it.download }
            ConnectionColumn.UPLOADED -> compareBy { it.upload }
            ConnectionColumn.DL_SPEED -> compareBy { it.dlSpeed }
            ConnectionColumn.UL_SPEED -> compareBy { it.ulSpeed }
            ConnectionColumn.CHAINS -> compareBy { it.chains.lowercase() }
            ConnectionColumn.RULE -> compareBy { it.rule.lowercase() }
            ConnectionColumn.PROCESS -> compareBy { it.process.lowercase() }
            ConnectionColumn.TIME -> compareBy { it.startEpochMs }
            ConnectionColumn.SOURCE -> compareBy { it.source }
            ConnectionColumn.DESTINATION -> compareBy { it.destination }
            ConnectionColumn.TYPE -> compareBy { it.type }
        }
        if (sortAscending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = context as? Activity

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "连接列表 (Connections)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "↓: ${BandwidthFormatter.formatTotalBytes(downloadTotal)}  ↑: ${BandwidthFormatter.formatTotalBytes(uploadTotal)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            onNavigateBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isPaused = !isPaused }
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "继续刷新" else "暂停刷新",
                            tint = if (isPaused) colors.brandPrimary else colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isLandscape) {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "横竖屏旋转切换",
                            tint = if (isLandscape) colors.brandPrimary else colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            isTableMode = !isTableMode
                            prefs.edit().putBoolean(KEY_TABLE_MODE, isTableMode).apply()
                        }
                    ) {
                        Icon(
                            imageVector = if (isTableMode) Icons.Default.ViewAgenda else Icons.Default.TableChart,
                            contentDescription = "切换表格/卡片视图",
                            tint = colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = { showColumnsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "显示列配置 (Columns)",
                            tint = colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = { closeAllConnections() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "全部关闭 (CLOSE ALL)",
                            tint = colors.statusDangerText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            // 1. Top Control Strip: [ACTIVE N | CLOSED M] + [CLOSE ALL] + Filter Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ACTIVE / CLOSED Segmented Pill matching Clash Meta Desktop
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.cardBackground)
                ) {
                    val activeSelected = activeTab == 0
                    Box(
                        modifier = Modifier
                            .background(if (activeSelected) colors.brandPrimary else colors.cardBackground)
                            .clickable { activeTab = 0 }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "ACTIVE ${activeConnections.size}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeSelected) colors.tabSelectedText else colors.brandPrimary
                        )
                    }
                    val closedSelected = activeTab == 1
                    Box(
                        modifier = Modifier
                            .background(if (closedSelected) colors.brandPrimary else colors.cardBackground)
                            .clickable { activeTab = 1 }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "CLOSED ${closedConnections.size}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (closedSelected) colors.tabSelectedText else colors.brandPrimary
                        )
                    }
                }

                // Filter Search Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (filterQuery.isEmpty()) {
                                Text(
                                    text = "Filter conditions",
                                    fontSize = 12.sp,
                                    color = colors.textMuted
                                )
                            }
                            BasicTextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    color = colors.textPrimary
                                ),
                                cursorBrush = SolidColor(colors.brandPrimary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (filterQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除",
                                tint = colors.textMuted,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { filterQuery = "" }
                            )
                        }
                    }
                }

                // CLOSE ALL Button matching desktop screenshot
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.brandPrimary,
                    modifier = Modifier
                        .height(34.dp)
                        .clickable { closeAllConnections() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = if (activeTab == 0) "CLOSE ALL" else "CLEAR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isRunning && activeTab == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mihomo 核心未运行，请先开启代理开关",
                            fontSize = 14.sp,
                            color = colors.textMuted
                        )
                    }
                }
            } else if (isTableMode) {
                // =========================================================================
                // Desktop-style Horizontal Scrollable Data Table View
                // =========================================================================
                val horizontalScrollState = rememberScrollState()
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        // Table Header Row
                        Row(
                            modifier = Modifier
                                .background(colors.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            visibleColumns.forEach { col ->
                                val isSorted = sortColumn == col
                                val arrow = if (isSorted) {
                                    if (sortAscending) " ▲" else " ▼"
                                } else ""
                                Text(
                                    text = "${col.titleEn}$arrow",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSorted) colors.brandPrimary else colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .width(col.width)
                                        .clickable {
                                            if (sortColumn == col) {
                                                sortAscending = !sortAscending
                                            } else {
                                                sortColumn = col
                                                sortAscending = col == ConnectionColumn.HOST
                                            }
                                        }
                                        .padding(end = 8.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = colors.innerBorder, thickness = 1.dp)

                        if (filteredAndSorted.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .width(500.dp)
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "   暂无匹配的连接记录",
                                    fontSize = 13.sp,
                                    color = colors.textMuted
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(
                                    items = filteredAndSorted,
                                    key = { it.id }
                                ) { conn ->
                                    Row(
                                        modifier = Modifier
                                            .clickable { selectedConnection = conn }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        visibleColumns.forEach { col ->
                                            val cellText = formatColumnValue(col, conn)
                                            val cellColor = when (col) {
                                                ConnectionColumn.HOST -> colors.textPrimary
                                                ConnectionColumn.DL_SPEED -> if (conn.dlSpeed > 0) colors.brandPrimary else colors.textMuted
                                                ConnectionColumn.UL_SPEED -> if (conn.ulSpeed > 0) colors.statusSuccessText else colors.textMuted
                                                ConnectionColumn.CHAINS -> colors.textSecondary
                                                else -> colors.textSecondary
                                            }
                                            Text(
                                                text = cellText,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (col == ConnectionColumn.HOST) FontWeight.Medium else FontWeight.Normal,
                                                color = cellColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .width(col.width)
                                                    .padding(end = 8.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = colors.innerBorder, thickness = 0.7.dp)
                                }
                            }
                        }
                    }
                }
            } else {
                // =========================================================================
                // Mobile Card View
                // =========================================================================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = filteredAndSorted,
                        key = { it.id }
                    ) { conn ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedConnection = conn },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            border = BorderStroke(1.dp, colors.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = conn.host,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.statusInfoBg
                                    ) {
                                        Text(
                                            text = conn.type,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.statusInfoText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "${conn.chains}  ·  ${conn.rule}",
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = buildString {
                                            append("↓ ${BandwidthFormatter.formatAdaptive(conn.dlSpeed)} (${BandwidthFormatter.formatTotalBytes(conn.download)})")
                                            append("   ↑ ${BandwidthFormatter.formatAdaptive(conn.ulSpeed)} (${BandwidthFormatter.formatTotalBytes(conn.upload)})")
                                        },
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = formatRelativeTime(conn.startEpochMs),
                                        fontSize = 11.sp,
                                        color = colors.textMuted
                                    )
                                }

                                if (conn.process.isNotBlank()) {
                                    Text(
                                        text = "进程: ${conn.process}",
                                        fontSize = 11.sp,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // Columns Selector Modal (Matches Screenshot 2: "Columns" + Checkboxes + RESET TO DEFAULT + CLOSE)
    // =========================================================================
    if (showColumnsDialog) {
        AlertDialog(
            onDismissRequest = { showColumnsDialog = false },
            containerColor = colors.cardBackground,
            title = {
                Text(
                    text = "Columns (显示字段)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConnectionColumn.entries.forEach { col ->
                        val checked = col in visibleColumns
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceSubtle,
                            border = BorderStroke(1.dp, colors.innerBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) {
                                        if (visibleColumns.size > 1) {
                                            visibleColumns.remove(col)
                                            saveVisibleColumns()
                                        }
                                    } else {
                                        val ordered = ConnectionColumn.entries.filter {
                                            it in visibleColumns || it == col
                                        }
                                        visibleColumns.clear()
                                        visibleColumns.addAll(ordered)
                                        saveVisibleColumns()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        if (!isChecked) {
                                            if (visibleColumns.size > 1) {
                                                visibleColumns.remove(col)
                                                saveVisibleColumns()
                                            }
                                        } else {
                                            val ordered = ConnectionColumn.entries.filter {
                                                it in visibleColumns || it == col
                                            }
                                            visibleColumns.clear()
                                            visibleColumns.addAll(ordered)
                                            saveVisibleColumns()
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = colors.brandPrimary,
                                        uncheckedColor = colors.textMuted
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${col.titleEn} (${col.titleZh})",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        visibleColumns.clear()
                        visibleColumns.addAll(ConnectionColumn.entries)
                        saveVisibleColumns()
                    }
                ) {
                    Text(
                        text = "RESET TO DEFAULT",
                        color = colors.brandPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showColumnsDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // =========================================================================
    // Connection Detail & Single Close Modal
    // =========================================================================
    selectedConnection?.let { conn ->
        AlertDialog(
            onDismissRequest = { selectedConnection = null },
            containerColor = colors.cardBackground,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "连接详情",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary
                    )
                    IconButton(
                        onClick = {
                            val summary = """
                                Host: ${conn.host}
                                Chains: ${conn.chains}
                                Rule: ${conn.rule}
                                Process: ${conn.process}
                                Source: ${conn.source}
                                Destination: ${conn.destination}
                                Type: ${conn.type}
                                Downloaded: ${BandwidthFormatter.formatTotalBytes(conn.download)}
                                Uploaded: ${BandwidthFormatter.formatTotalBytes(conn.upload)}
                            """.trimIndent()
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Connection Detail", summary))
                            Toast.makeText(context, "已复制连接信息", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            tint = colors.brandPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConnectionColumn.entries.forEach { col ->
                        Column {
                            Text(
                                text = "${col.titleEn} (${col.titleZh})",
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = formatColumnValue(col, conn).ifBlank { "-" },
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            },
            dismissButton = {
                if (!conn.isClosed) {
                    TextButton(
                        onClick = {
                            closeConnectionById(conn.id)
                            selectedConnection = null
                        }
                    ) {
                        Text(
                            text = "断开此连接",
                            color = colors.statusDangerText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedConnection = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("关闭")
                }
            }
        )
    }
}

private fun formatColumnValue(col: ConnectionColumn, conn: ClashConnectionItem): String {
    return when (col) {
        ConnectionColumn.HOST -> conn.host
        ConnectionColumn.DOWNLOADED -> BandwidthFormatter.formatTotalBytes(conn.download)
        ConnectionColumn.UPLOADED -> BandwidthFormatter.formatTotalBytes(conn.upload)
        ConnectionColumn.DL_SPEED -> formatConnectionSpeed(conn.dlSpeed)
        ConnectionColumn.UL_SPEED -> formatConnectionSpeed(conn.ulSpeed)
        ConnectionColumn.CHAINS -> conn.chains
        ConnectionColumn.RULE -> conn.rule
        ConnectionColumn.PROCESS -> conn.process
        ConnectionColumn.TIME -> formatRelativeTime(conn.startEpochMs)
        ConnectionColumn.SOURCE -> conn.source
        ConnectionColumn.DESTINATION -> conn.destination
        ConnectionColumn.TYPE -> conn.type
    }
}

private fun formatConnectionSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0.00 B/s"
    return BandwidthFormatter.formatAdaptive(bytesPerSec)
}

private fun parseIsoMillis(isoStr: String): Long {
    if (isoStr.isBlank()) return System.currentTimeMillis()
    return try {
        Instant.parse(isoStr).toEpochMilli()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }
}

private fun formatRelativeTime(startEpochMs: Long): String {
    val diffSec = ((System.currentTimeMillis() - startEpochMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 45L -> "a few seconds ago"
        diffSec < 90L -> "a minute ago"
        diffSec < 3600L -> "${diffSec / 60L} minutes ago"
        diffSec < 5400L -> "an hour ago"
        diffSec < 86400L -> "${diffSec / 3600L} hours ago"
        else -> String.format(Locale.US, "%d days ago", diffSec / 86400L)
    }
}
