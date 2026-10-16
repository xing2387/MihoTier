package top.xing2387.mihotier.ui.screens

import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import top.xing2387.mihotier.ui.theme.AppTheme
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
    val colors = AppTheme.colors
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
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
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
                            tint = if (isRunning) (if (isPaused) colors.brandPrimary else colors.textPrimary) else colors.textMuted
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
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        },
        containerColor = colors.background
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
                    border = BorderStroke(1.dp, colors.cardBorder),
                    color = colors.cardBackground
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
                            tint = colors.textMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索日志...",
                                    fontSize = 12.sp,
                                    color = colors.textMuted
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    color = colors.textPrimary
                                ),
                                cursorBrush = SolidColor(colors.brandPrimary)
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = colors.textMuted,
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
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isAutoScroll,
                        onCheckedChange = { isAutoScroll = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.cardBackground,
                            checkedTrackColor = colors.brandPrimary,
                            uncheckedThumbColor = colors.cardBackground,
                            uncheckedTrackColor = colors.innerBorder
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
                        color = if (isSelected) (if (colors.isDark) colors.brandPrimary.copy(alpha = 0.25f) else Color(0xFFBAE6FD)) else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) colors.brandPrimary else colors.cardBorder
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = level,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) (if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)) else colors.textSecondary
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
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(1.dp, colors.cardBorder)
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
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "代理未开启，开启代理后自动连接实时日志",
                                    fontSize = 13.sp,
                                    color = colors.textMuted
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
                                color = colors.textMuted
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
                        color = colors.surfaceVariant,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Scroll down",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "回到最新",
                                color = colors.textPrimary,
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
    val colors = AppTheme.colors

    // Semantic Badge Color Mapping
    val (badgeBg, badgeText) = remember(entry.tag, entry.level, colors.isDark) {
        when (entry.tag.uppercase()) {
            "RULE", "INFO" -> Pair(colors.statusInfoBg, colors.statusInfoText)
            "DNS", "WARN", "WARNING" -> Pair(colors.statusWarningBg, colors.statusWarningText)
            "TCP", "ERROR" -> Pair(colors.statusDangerBg, colors.statusDangerText)
            "DEBUG" -> Pair(colors.surfaceSubtle, colors.textMuted)
            else -> {
                when (entry.level.lowercase()) {
                    "error" -> Pair(colors.statusDangerBg, colors.statusDangerText)
                    "warn", "warning" -> Pair(colors.statusWarningBg, colors.statusWarningText)
                    "debug" -> Pair(colors.surfaceSubtle, colors.textMuted)
                    else -> Pair(colors.statusInfoBg, colors.statusInfoText)
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
            color = colors.textMuted,
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
        val annotatedMessage = remember(entry.message, searchQuery, colors.isDark) {
            if (searchQuery.isBlank()) {
                buildAnnotatedString { append(entry.message) }
            } else {
                buildAnnotatedString {
                    val text = entry.message
                    val q = searchQuery.trim()
                    val highlightBg = if (colors.isDark) colors.brandPrimary.copy(alpha = 0.35f) else Color(0xFFFEF08A)
                    val highlightText = if (colors.isDark) Color.White else Color(0xFF0F172A)
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
                        withStyle(SpanStyle(background = highlightBg, color = highlightText, fontWeight = FontWeight.Bold)) {
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
            color = colors.textPrimary,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}
