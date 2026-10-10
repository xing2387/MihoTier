package top.xing2387.mihotier.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LogEntry(
    val id: Long,
    val time: String,
    val tag: String,
    val level: String,
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashLogsScreen(
    isRunning: Boolean = false,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // In-memory log buffer (capped at 1000 items to avoid OOM)
    val logs = remember { mutableStateListOf<LogEntry>() }

    // Control States
    val logLevels = listOf("DEBUG", "INFO", "WARN", "ERROR", "SILENT")
    var selectedLevel by remember { mutableStateOf("INFO") }
    var isPaused by remember { mutableStateOf(false) }
    var isAutoScroll by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // HTTP client for hot PATCHing log-level & WebSocket
    val okHttpClient = remember {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // Function to hot-update Clash core log-level via REST API
    fun updateLogLevel(level: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        selectedLevel = level
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val body = "{\"log-level\":\"${level.lowercase()}\"}".toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("http://127.0.0.1:9090/configs")
                    .patch(body)
                    .build()
                okHttpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                // Ignore API error if core is busy
            }
        }
    }

    // WebSocket Log Stream Subscription
    LaunchedEffect(isRunning, selectedLevel) {
        if (!isRunning) return@LaunchedEffect

        val wsUrl = "ws://127.0.0.1:9090/logs?level=${selectedLevel.lowercase()}"
        val request = Request.Builder().url(wsUrl).build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (isPaused) return // Freeze stream when paused
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type", "info")
                    val payload = json.optString("payload", "")

                    // Parse module tag [Rule], [DNS], [TCP], etc.
                    var tag = type.uppercase()
                    var msg = payload
                    if (payload.startsWith("[") && payload.contains("]")) {
                        val endBracket = payload.indexOf("]")
                        tag = payload.substring(1, endBracket).trim()
                        msg = payload.substring(endBracket + 1).trimStart()
                    }

                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val entry = LogEntry(
                        id = System.nanoTime(),
                        time = timeStr,
                        tag = tag,
                        level = type,
                        message = msg
                    )

                    coroutineScope.launch(Dispatchers.Main) {
                        // Cap buffer to 1000 lines
                        if (logs.size >= 1000) {
                            logs.removeRange(0, 100)
                        }
                        logs.add(entry)
                    }
                } catch (e: Exception) {
                    // Ignore parse error
                }
            }
        }

        val ws = okHttpClient.newWebSocket(request, listener)
        try {
            awaitCancellation()
        } finally {
            ws.close(1000, "Leaving screen")
        }
    }

    // Auto-scroll to bottom when new logs arrive (if auto-scroll enabled)
    val filteredLogs = remember(logs.size, searchQuery) {
        if (searchQuery.isBlank()) {
            logs.toList()
        } else {
            val q = searchQuery.trim().lowercase()
            logs.filter {
                it.message.lowercase().contains(q) ||
                it.tag.lowercase().contains(q) ||
                it.time.contains(q)
            }
        }
    }

    LaunchedEffect(filteredLogs.size, isAutoScroll) {
        if (isAutoScroll && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    // Detect user scrolling up to pause auto-scroll
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (filteredLogs.isNotEmpty() && lastVisible < filteredLogs.size - 2) {
                isAutoScroll = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "实时日志 (Logs)",
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
                actions = {
                    // 1. Pause / Resume Button (borderless icon button, disabled when proxy not connected)
                    IconButton(
                        onClick = {
                            isPaused = !isPaused
                            Toast.makeText(
                                context,
                                if (isPaused) "日志流已暂停" else "日志流已恢复",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        enabled = isRunning
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isRunning) Color(0xFF0F172A) else Color(0xFF94A3B8)
                        )
                    }

                    // 2. Clear Logs
                    IconButton(onClick = {
                        logs.clear()
                        Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Logs",
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Search Box (left, sticking to parent) & Auto-scroll Switch (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索日志...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    color = Color(0xFF0F172A)
                                ),
                                cursorBrush = SolidColor(Color(0xFF0284C7))
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = Color(0xFF64748B),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Auto-scroll control
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自动滚动",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isAutoScroll,
                        onCheckedChange = { isAutoScroll = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF5B7EAC),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Log Level Section (Segmented Filter Chips)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                logLevels.forEach { level ->
                    val isSelected = (selectedLevel.equals(level, ignoreCase = true))
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { updateLogLevel(level) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFFBAE6FD) else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color(0xFFCBD5E1)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = level,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0369A1) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Log Stream Area (Card Container)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    if (!isRunning) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "代理未开启，开启代理后自动连接实时日志",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "未匹配到相关日志" else "等待日志流输出...",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp)
                        ) {
                            items(
                                items = filteredLogs,
                                key = { it.id }
                            ) { logItem ->
                                LogRowItem(
                                    entry = logItem,
                                    searchQuery = searchQuery
                                )
                            }
                        }
                    }
                }

                // Floating "Back to Bottom" (回到最新) Pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = (!isAutoScroll && filteredLogs.isNotEmpty()),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                isAutoScroll = true
                                coroutineScope.launch {
                                    listState.animateScrollToItem(filteredLogs.size - 1)
                                }
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0F172A),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Scroll down",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "回到最新",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun LogRowItem(
    entry: LogEntry,
    searchQuery: String
) {
    // Semantic Badge Color Mapping
    val (badgeBg, badgeText) = remember(entry.tag, entry.level) {
        when (entry.tag.uppercase()) {
            "RULE", "INFO" -> Pair(Color(0xFFBAE6FD), Color(0xFF0369A1)) // Sky blue
            "DNS", "WARN", "WARNING" -> Pair(Color(0xFFFED7AA), Color(0xFFC2410C)) // Amber orange
            "TCP", "ERROR" -> Pair(Color(0xFFFECACA), Color(0xFFDC2626)) // Coral red
            "DEBUG" -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B)) // Slate gray
            else -> {
                when (entry.level.lowercase()) {
                    "error" -> Pair(Color(0xFFFECACA), Color(0xFFDC2626))
                    "warn", "warning" -> Pair(Color(0xFFFED7AA), Color(0xFFC2410C))
                    "debug" -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
                    else -> Pair(Color(0xFFBAE6FD), Color(0xFF0369A1))
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp (Monospace)
        Text(
            text = entry.time,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 1.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Badge Tag [Rule], [DNS], [TCP], etc.
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = badgeBg
        ) {
            Text(
                text = "[${entry.tag}]",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = badgeText,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Message text with optional keyword highlight
        val annotatedMessage = remember(entry.message, searchQuery) {
            if (searchQuery.isBlank()) {
                buildAnnotatedString { append(entry.message) }
            } else {
                buildAnnotatedString {
                    val text = entry.message
                    val q = searchQuery.trim()
                    var startIndex = 0
                    while (startIndex < text.length) {
                        val index = text.indexOf(q, startIndex, ignoreCase = true)
                        if (index < 0) {
                            append(text.substring(startIndex))
                            break
                        }
                        if (index > startIndex) {
                            append(text.substring(startIndex, index))
                        }
                        withStyle(SpanStyle(background = Color(0xFFFEF08A), fontWeight = FontWeight.Bold)) {
                            append(text.substring(index, index + q.length))
                        }
                        startIndex = index + q.length
                    }
                }
            }
        }

        Text(
            text = annotatedMessage,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF0F172A),
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}
