package top.xing2387.mihotier.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import top.xing2387.mihotier.core.config.EasyTierRepository
import top.xing2387.mihotier.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyTierLogsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val runtimeState by EasyTierRepository.runtimeState.collectAsState()
    val eventLogs = runtimeState.eventLogs
    var isFormatJson by remember { mutableStateOf(true) }

    val logContent = remember(eventLogs, isFormatJson) {
        if (eventLogs.isEmpty()) {
            "暂无事件日志记录..."
        } else if (isFormatJson) {
            eventLogs.joinToString("\n\n") { entry ->
                formatJsonLogEntry(entry)
            }
        } else {
            eventLogs.joinToString("\n") { entry ->
                entry.lines().joinToString(" ") { it.trim() }
            }
        }
    }

    // Refresh status while active
    LaunchedEffect(runtimeState.isRunning) {
        while (isActive && runtimeState.isRunning) {
            EasyTierRepository.refreshRuntimeStatus()
            delay(1500L)
        }
    }

    var editorView by remember { mutableStateOf<CodeEditor?>(null) }
    var isWordWrap by remember { mutableStateOf(true) }
    var autoScrollToBottom by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatchCount by remember { mutableIntStateOf(0) }
    var searchMatchIndex by remember { mutableIntStateOf(-1) }
    val searchFocusRequester = remember { FocusRequester() }

    fun scrollToTargetPosition(editor: CodeEditor) {
        val cursor = editor.cursor
        val line = cursor.leftLine
        val col = cursor.leftColumn
        val charAbsoluteY = editor.getCharOffsetY(line, col) + editor.offsetY
        val rowHeight = if (editor.rowHeight > 0) editor.rowHeight else 50
        val offsetFromTop = if (editor.height > 0) {
            (editor.height / 6).coerceAtLeast(rowHeight * 5)
        } else {
            rowHeight * 5
        }
        val targetScrollY = (charAbsoluteY - offsetFromTop).toInt().coerceIn(0, editor.scrollMaxY)
        val currentX = editor.offsetX
        val currentY = editor.offsetY
        val targetX = editor.scroller.finalX
        val dx = targetX - currentX
        val dy = targetScrollY - currentY

        editor.scroller.forceFinished(true)
        if (dx != 0 || dy != 0) {
            editor.scroller.startScroll(currentX, currentY, dx, dy, 200)
            editor.postInvalidateOnAnimation()
        }
    }

    fun scrollToBottom(editor: CodeEditor, smooth: Boolean = true) {
        try {
            val totalLines = editor.lineCount
            if (totalLines > 0) {
                editor.jumpToLine((totalLines - 1).coerceAtLeast(0))
                val targetScrollY = editor.scrollMaxY
                val currentY = editor.offsetY
                val dy = targetScrollY - currentY
                editor.scroller.forceFinished(true)
                if (dy != 0) {
                    if (smooth) {
                        editor.scroller.startScroll(editor.offsetX, currentY, 0, dy, 120)
                        editor.postInvalidateOnAnimation()
                    } else {
                        editor.scroller.startScroll(editor.offsetX, currentY, 0, dy, 0)
                        editor.postInvalidateOnAnimation()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun stopEditorSearch() {
        val searcher = editorView?.searcher
        if (searcher?.hasQuery() == true) {
            try {
                searcher.stopSearch()
            } catch (_: Exception) {}
        }
        searchMatchCount = 0
        searchMatchIndex = -1
    }

    fun gotoNextMatch() {
        val editor = editorView ?: return
        val searcher = editor.searcher
        if (searcher.hasQuery()) {
            try {
                if (searcher.gotoNext()) {
                    searchMatchIndex = searcher.currentMatchedPositionIndex
                    scrollToTargetPosition(editor)
                }
            } catch (_: Exception) {}
        }
    }

    fun gotoPrevMatch() {
        val editor = editorView ?: return
        val searcher = editor.searcher
        if (searcher.hasQuery()) {
            try {
                if (searcher.gotoPrevious()) {
                    searchMatchIndex = searcher.currentMatchedPositionIndex
                    scrollToTargetPosition(editor)
                }
            } catch (_: Exception) {}
        }
    }

    // Debounced Search Effect
    LaunchedEffect(searchQuery, isSearchActive) {
        val searcher = editorView?.searcher
        if (!isSearchActive || searchQuery.isEmpty()) {
            stopEditorSearch()
        } else {
            delay(120)
            try {
                searcher?.search(searchQuery, EditorSearcher.SearchOptions(false, false))
            } catch (_: Exception) {}
        }
    }

    // Keep Editor text synchronized with EasyTier logs
    LaunchedEffect(logContent) {
        editorView?.let { editor ->
            val cur = editor.text.toString()
            if (cur != logContent) {
                val prevOffsetX = editor.offsetX
                val prevOffsetY = editor.offsetY

                if (cur.isNotEmpty() && logContent.startsWith(cur) && cur != "暂无事件日志记录...") {
                    val appendPart = logContent.substring(cur.length)
                    val lastLine = (editor.lineCount - 1).coerceAtLeast(0)
                    val lastCol = editor.text.getColumnCount(lastLine)
                    editor.text.insert(lastLine, lastCol, appendPart)
                } else {
                    editor.setText(logContent)
                }

                if (autoScrollToBottom) {
                    delay(30)
                    scrollToBottom(editor, smooth = true)
                } else {
                    editor.scroller.forceFinished(true)
                    val safeY = prevOffsetY.coerceIn(0, editor.scrollMaxY)
                    editor.scroller.startScroll(prevOffsetX, safeY, 0, 0, 0)
                    editor.postInvalidateOnAnimation()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            editorView?.release()
        }
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "事件日志",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "共 ${eventLogs.size} 条记录",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary
                            )
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
                        // 1. Search Toggle
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) {
                                stopEditorSearch()
                                searchQuery = ""
                            }
                        }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "搜索日志",
                                tint = if (isSearchActive) AppTheme.colors.brandPrimary else AppTheme.colors.textPrimary
                            )
                        }

                        // 2. Word Wrap Toggle
                        IconButton(onClick = {
                            isWordWrap = !isWordWrap
                            editorView?.setWordwrap(isWordWrap)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.WrapText,
                                contentDescription = "自动换行",
                                tint = if (isWordWrap) AppTheme.colors.brandPrimary else AppTheme.colors.textSecondary
                            )
                        }

                        // 3. JSON Format Toggle
                        IconButton(onClick = {
                            isFormatJson = !isFormatJson
                        }) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "JSON 格式化",
                                tint = if (isFormatJson) AppTheme.colors.brandPrimary else AppTheme.colors.textSecondary
                            )
                        }

                        // 4. Scroll to Bottom
                        IconButton(onClick = {
                            editorView?.let { scrollToBottom(it, smooth = true) }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "滚动至末尾",
                                tint = AppTheme.colors.textPrimary
                            )
                        }

                        // 5. More Options Menu
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多",
                                    tint = AppTheme.colors.textPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isFormatJson) "切换至单行紧凑模式" else "切换至 JSON 格式化模式") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = if (isFormatJson) AppTheme.colors.brandPrimary else AppTheme.colors.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        isFormatJson = !isFormatJson
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("复制全部日志") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("EasyTier Logs", logContent))
                                        Toast.makeText(context, "已复制全部日志到剪贴板", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("清空事件记录", color = AppTheme.colors.statusDangerText) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = AppTheme.colors.statusDangerText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        EasyTierRepository.clearEventLogs()
                                        Toast.makeText(context, "已清空事件日志", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.cardBackground)
                )

                // Inline Search Bar
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = AppTheme.colors.cardBackground,
                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(AppTheme.colors.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜索事件日志关键词...",
                                        fontSize = 13.sp,
                                        color = AppTheme.colors.textMuted
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    textStyle = TextStyle(
                                        color = AppTheme.colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(AppTheme.colors.brandPrimary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                )
                            }

                            LaunchedEffect(isSearchActive) {
                                if (isSearchActive) {
                                    delay(100)
                                    searchFocusRequester.requestFocus()
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = if (searchQuery.isEmpty()) {
                                    ""
                                } else if (searchMatchCount > 0) {
                                    "${searchMatchIndex + 1}/$searchMatchCount"
                                } else {
                                    "0/0"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (searchMatchCount > 0) AppTheme.colors.brandPrimary else AppTheme.colors.statusDangerText
                            )

                            IconButton(
                                onClick = { gotoPrevMatch() },
                                enabled = searchMatchCount > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "上一处匹配",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { gotoNextMatch() },
                                enabled = searchMatchCount > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "下一处匹配",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = AppTheme.colors.cardBorder, thickness = 1.dp)
            }
        },
        bottomBar = {
            Surface(
                color = AppTheme.colors.cardBackground,
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppTheme.colors.statusInfoBg
                        ) {
                            Text(
                                text = "只读模式",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.statusInfoText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isFormatJson) AppTheme.colors.statusSuccessBg else AppTheme.colors.surfaceVariant
                        ) {
                            Text(
                                text = if (isFormatJson) "JSON 格式化" else "紧凑单行",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFormatJson) AppTheme.colors.statusSuccessText else AppTheme.colors.textMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "${editorView?.text?.lineCount ?: eventLogs.size}行",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppTheme.colors.textMuted
                        )
                    }

                    // 始终滚动到末尾 开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            val next = !autoScrollToBottom
                            autoScrollToBottom = next
                            if (next) {
                                editorView?.let { scrollToBottom(it, smooth = true) }
                            }
                        }
                    ) {
                        Text(
                            text = "滚动到末尾",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (autoScrollToBottom) AppTheme.colors.brandPrimary else AppTheme.colors.textSecondary
                        )
                        Switch(
                            checked = autoScrollToBottom,
                            onCheckedChange = { checked ->
                                autoScrollToBottom = checked
                                if (checked) {
                                    editorView?.let { scrollToBottom(it, smooth = true) }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AppTheme.colors.cardBackground,
                                checkedTrackColor = AppTheme.colors.brandPrimary,
                                uncheckedThumbColor = AppTheme.colors.cardBackground,
                                uncheckedTrackColor = AppTheme.colors.innerBorder
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AppTheme.colors.terminalBg)
        ) {
            val textColor = AppTheme.colors.terminalText.toArgb()
            val gutterText = AppTheme.colors.textMuted.toArgb()
            val gutterBg = AppTheme.colors.surfaceVariant.toArgb()
            val divider = AppTheme.colors.innerBorder.toArgb()
            val brandPrimary = AppTheme.colors.brandPrimary.toArgb()
            val currentLine = AppTheme.colors.brandPrimary.copy(alpha = 0.08f).toArgb()
            val selection = AppTheme.colors.brandPrimary.copy(alpha = 0.35f).toArgb()
            val matchedTextBg = AppTheme.colors.brandPrimary.copy(alpha = 0.30f).toArgb()

            AndroidView<CodeEditor>(
                factory = { ctx ->
                    CodeEditor(ctx).apply {
                        editorView = this
                        typefaceText = Typeface.MONOSPACE
                        typefaceLineNumber = Typeface.MONOSPACE
                        setTextSize(13f)
                        setWordwrap(isWordWrap)
                        setEditable(false) // Read-only as requested!
                        isLineNumberEnabled = true
                        setScalable(true)

                        val scheme = colorScheme
                        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, android.graphics.Color.TRANSPARENT)
                        scheme.setColor(EditorColorScheme.TEXT_NORMAL, textColor)
                        scheme.setColor(EditorColorScheme.LINE_NUMBER, gutterText)
                        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, brandPrimary)
                        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, gutterBg)
                        scheme.setColor(EditorColorScheme.LINE_DIVIDER, divider)
                        scheme.setColor(EditorColorScheme.CURRENT_LINE, currentLine)
                        scheme.setColor(EditorColorScheme.SELECTION_INSERT, brandPrimary)
                        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selection)
                        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, brandPrimary)
                        scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, matchedTextBg)

                        setText(logContent)

                        subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
                            val s = this@apply.searcher
                            if (s.hasQuery()) {
                                try {
                                    searchMatchCount = s.matchedPositionCount
                                    searchMatchIndex = s.currentMatchedPositionIndex
                                } catch (_: Exception) {
                                    searchMatchCount = 0
                                    searchMatchIndex = -1
                                }
                            } else {
                                searchMatchCount = 0
                                searchMatchIndex = -1
                            }
                        }

                        // Initially jump to bottom so the latest logs are visible
                        post {
                            scrollToBottom(this)
                        }
                    }
                },
                update = { editor ->
                    editor.setWordwrap(isWordWrap)
                    editor.setEditable(false)
                    val scheme = editor.colorScheme
                    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, android.graphics.Color.TRANSPARENT)
                    scheme.setColor(EditorColorScheme.TEXT_NORMAL, textColor)
                    scheme.setColor(EditorColorScheme.LINE_NUMBER, gutterText)
                    scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, brandPrimary)
                    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, gutterBg)
                    scheme.setColor(EditorColorScheme.LINE_DIVIDER, divider)
                    scheme.setColor(EditorColorScheme.CURRENT_LINE, currentLine)
                    scheme.setColor(EditorColorScheme.SELECTION_INSERT, brandPrimary)
                    scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selection)
                    scheme.setColor(EditorColorScheme.SELECTION_HANDLE, brandPrimary)
                    scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, matchedTextBg)
                    editor.onColorFullUpdate()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun formatJsonLogEntry(entry: String, indent: Int = 2): String {
    val trimmed = entry.trim()
    if (trimmed.isEmpty()) return entry

    // 1. Check if the line is purely a JSON object or array
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        return try {
            JSONObject(trimmed).toString(indent)
        } catch (_: Exception) {
            entry
        }
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]") && !trimmed.startsWith("[ERROR]")) {
        return try {
            JSONArray(trimmed).toString(indent)
        } catch (_: Exception) {
            entry
        }
    }

    // 2. Check if the line has a log prefix, e.g. "[13:01:07] PeerAdded: {...}"
    val colonIdx = trimmed.indexOf(":")
    if (colonIdx != -1 && colonIdx < trimmed.length - 1) {
        val prefix = trimmed.substring(0, colonIdx + 1)
        val payload = trimmed.substring(colonIdx + 1).trim()
        if (payload.startsWith("{") && payload.endsWith("}")) {
            return try {
                "$prefix\n${JSONObject(payload).toString(indent)}"
            } catch (_: Exception) {
                entry
            }
        } else if (payload.startsWith("[") && payload.endsWith("]")) {
            return try {
                "$prefix\n${JSONArray(payload).toString(indent)}"
            } catch (_: Exception) {
                entry
            }
        }
    }

    return entry
}
