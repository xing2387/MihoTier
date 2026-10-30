package top.xing2387.mihotier.ui.screens

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.xing2387.mihotier.core.config.ProfileRepository
import top.xing2387.mihotier.core.vpn.UnifiedVpnService
import top.xing2387.mihotier.ui.theme.AppTheme
import top.xing2387.mihotier.ui.theme.appTextFieldColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashConfigYamlEditorScreen(
    configFile: File,
    displayFileName: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var loadedText by remember { mutableStateOf("") }
    var lineCount by remember { mutableIntStateOf(0) }
    var charCount by remember { mutableIntStateOf(0) }
    var lastSavedModified by remember {
        mutableStateOf(if (configFile.exists()) configFile.lastModified() else 0L)
    }

    var isWordWrap by remember { mutableStateOf(false) }
    var isReadOnly by remember { mutableStateOf(false) }
    var showLineJumpDialog by remember { mutableStateOf(false) }
    var jumpLineInput by remember { mutableStateOf("") }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatchCount by remember { mutableIntStateOf(0) }
    var searchMatchIndex by remember { mutableIntStateOf(-1) }
    val searchFocusRequester = remember { FocusRequester() }

    // Professional Piece-Tree code editor reference
    var editorView by remember { mutableStateOf<CodeEditor?>(null) }

    fun scrollToTargetPosition(editor: CodeEditor) {
        val cursor = editor.cursor
        val line = cursor.leftLine
        val col = cursor.leftColumn
        val charAbsoluteY = editor.getCharOffsetY(line, col) + editor.offsetY
        val rowHeight = if (editor.rowHeight > 0) editor.rowHeight else 50
        // Position target at ~1/6 from top, or at row 6 (~5 rows above it)
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

    // Intercept system back button: close search first if active, otherwise navigate back
    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            stopEditorSearch()
        } else {
            onNavigateBack()
        }
    }

    // Auto-focus search input when search is opened
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Debounced asynchronous background search using Sora Editor's built-in Searcher
    LaunchedEffect(searchQuery, isSearchActive) {
        val searcher = editorView?.searcher
        if (!isSearchActive || searchQuery.isEmpty()) {
            stopEditorSearch()
        } else {
            delay(120) // 120ms debounce for typing
            try {
                searcher?.setCyclicJumping(true)
                searcher?.search(
                    searchQuery,
                    EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true)
                )
            } catch (_: Exception) {}
        }
    }

    // Asynchronous background load to prevent blocking main thread on launch
    LaunchedEffect(configFile) {
        withContext(Dispatchers.IO) {
            val text = if (configFile.exists()) configFile.readText() else ""
            val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
            withContext(Dispatchers.Main) {
                loadedText = text
                lineCount = lines
                charCount = text.length
                isLoading = false
            }
        }
    }

    // Auto-reload if modified externally and cleanup on dispose
    DisposableEffect(lifecycleOwner, configFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && configFile.exists()) {
                val currentModified = configFile.lastModified()
                if (currentModified > lastSavedModified) {
                    coroutineScope.launch {
                        val text = withContext(Dispatchers.IO) { configFile.readText() }
                        loadedText = text
                        editorView?.setText(text)
                        lineCount = editorView?.text?.lineCount ?: (if (text.isEmpty()) 0 else text.count { it == '\n' } + 1)
                        charCount = text.length
                        lastSavedModified = currentModified
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            editorView?.release()
        }
    }

    fun saveConfigToDisk(showToast: Boolean = true): Boolean {
        val currentText = editorView?.text?.toString() ?: loadedText
        return try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(currentText)
            lastSavedModified = configFile.lastModified()
            lineCount = editorView?.text?.lineCount ?: (if (currentText.isEmpty()) 0 else currentText.count { it == '\n' } + 1)
            charCount = currentText.length
            val active = ProfileRepository.getActiveProfile()
            if (active != null && configFile.name == "${active.id}.yaml") {
                UnifiedVpnService.reloadIfRunning(context)
            }
            if (showToast) {
                Toast.makeText(context, "配置文件已保存", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openInExternalEditor() {
        if (!saveConfigToDisk(showToast = false)) return

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                configFile
            )
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK

            // Primary intent: ACTION_VIEW with text/plain
            val baseIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                clipData = ClipData.newRawUri(displayFileName, uri)
                addFlags(flags)
            }

            val pm = context.packageManager
            fun queryActivities(intent: Intent): List<android.content.pm.ResolveInfo> {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }
            }

            val baseMatches = queryActivities(baseIntent)
            val knownComponentKeys = baseMatches.map {
                "${it.activityInfo.packageName}/${it.activityInfo.name}"
            }.toMutableSet()
            val targetPackages = baseMatches.map { it.activityInfo.packageName }.toMutableSet()

            val secondaryIntentTemplates = listOf(
                Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(uri, "text/plain")
                    clipData = ClipData.newRawUri(displayFileName, uri)
                    addFlags(flags)
                },
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    clipData = ClipData.newRawUri(displayFileName, uri)
                    addFlags(flags)
                },
                Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(uri, "*/*")
                    clipData = ClipData.newRawUri(displayFileName, uri)
                    addFlags(flags)
                }
            )

            val extraIntents = mutableListOf<Intent>()
            for (template in secondaryIntentTemplates) {
                val matches = queryActivities(template)
                for (match in matches) {
                    val key = "${match.activityInfo.packageName}/${match.activityInfo.name}"
                    if (!knownComponentKeys.contains(key)) {
                        knownComponentKeys.add(key)
                        targetPackages.add(match.activityInfo.packageName)
                        val specificIntent = Intent(template).apply {
                            setClassName(match.activityInfo.packageName, match.activityInfo.name)
                            setDataAndType(template.data, template.type)
                            clipData = ClipData.newRawUri(displayFileName, uri)
                            addFlags(flags)
                        }
                        extraIntents.add(specificIntent)
                    }
                }
            }

            // Explicitly pre-grant read and write URI permissions to all candidate packages
            for (pkg in targetPackages) {
                try {
                    context.grantUriPermission(
                        pkg,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }

            // Create chooser with baseIntent (which displays HtmlViewer, etc.) + all extra editors
            val chooser = Intent.createChooser(baseIntent, "选择外部应用打开或编辑").apply {
                if (extraIntents.isNotEmpty()) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toTypedArray())
                }
                addFlags(flags)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "未找到可用的外部应用: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(
                                    color = AppTheme.colors.surfaceVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = AppTheme.colors.brandPrimary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = AppTheme.colors.brandPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜索配置文件...",
                                        fontSize = 13.5.sp,
                                        color = AppTheme.colors.textMuted
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        color = AppTheme.colors.textPrimary
                                    ),
                                    cursorBrush = SolidColor(AppTheme.colors.brandPrimary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { gotoNextMatch() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = AppTheme.colors.textMuted,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            searchQuery = ""
                                            stopEditorSearch()
                                        }
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                            stopEditorSearch()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search",
                                tint = AppTheme.colors.textPrimary
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            if (searchMatchCount > 0) {
                                Text(
                                    text = "${searchMatchIndex + 1}/$searchMatchCount",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.brandPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            } else {
                                Text(
                                    text = "无结果",
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.textMuted,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }

                            IconButton(
                                onClick = { gotoPrevMatch() },
                                enabled = searchMatchCount > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Previous Match",
                                    tint = if (searchMatchCount > 0) AppTheme.colors.textPrimary else AppTheme.colors.textMuted
                                )
                            }

                            IconButton(
                                onClick = { gotoNextMatch() },
                                enabled = searchMatchCount > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Next Match",
                                    tint = if (searchMatchCount > 0) AppTheme.colors.textPrimary else AppTheme.colors.textMuted
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                    stopEditorSearch()
                                } else {
                                    isSearchActive = false
                                    stopEditorSearch()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear / Close",
                                tint = AppTheme.colors.textSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppTheme.colors.background
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "编辑配置文件",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = displayFileName,
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                    actions = {
                        // 1. Search Action Button
                        IconButton(onClick = {
                            isSearchActive = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = AppTheme.colors.textPrimary
                            )
                        }

                        // 2. Word Wrap Toggle
                        IconButton(onClick = {
                            isWordWrap = !isWordWrap
                            editorView?.setWordwrap(isWordWrap)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.WrapText,
                                contentDescription = "Word Wrap",
                                tint = if (isWordWrap) AppTheme.colors.brandPrimary else AppTheme.colors.textSecondary
                            )
                        }

                        // 3. Read Only Toggle
                        IconButton(onClick = {
                            isReadOnly = !isReadOnly
                            editorView?.setEditable(!isReadOnly)
                            if (isReadOnly) {
                                editorView?.hideSoftInput()
                            }
                            Toast.makeText(
                                context,
                                if (isReadOnly) "已切换至「浏览模式」" else "已切换至「编辑模式」",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                imageVector = if (isReadOnly) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = "Read Only Toggle",
                                tint = if (isReadOnly) AppTheme.colors.statusWarningText else AppTheme.colors.textSecondary
                            )
                        }

                        // 4. Open in External App
                        IconButton(onClick = { openInExternalEditor() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External App",
                                tint = AppTheme.colors.textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppTheme.colors.background
                    )
                )
            }
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Metadata Header: Mode & Statistics
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isReadOnly) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppTheme.colors.statusWarningBg
                                ) {
                                    Text(
                                        text = "只读模式",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppTheme.colors.statusWarningText,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            if (isWordWrap) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppTheme.colors.surfaceVariant
                                ) {
                                    Text(
                                        text = "自动折行",
                                        fontSize = 11.sp,
                                        color = AppTheme.colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${lineCount} 行 · ${charCount} 字符",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Quick Jump Line Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceVariant,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                    jumpLineInput = ""
                                    showLineJumpDialog = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tag,
                                        contentDescription = "Jump Line",
                                        tint = AppTheme.colors.brandPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "跳行",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.brandPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Ultra-fast CodeEditor Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surfaceVariant.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = AppTheme.colors.brandPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "正在快速载入配置文件...",
                                    fontSize = 13.sp,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        } else {
                            val textColor = AppTheme.colors.textPrimary.toArgb()
                            val gutterText = AppTheme.colors.textMuted.toArgb()
                            val gutterBg = AppTheme.colors.surfaceVariant.toArgb()
                            val divider = AppTheme.colors.divider.toArgb()
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
                                        setTextSize(13.5f)
                                        setWordwrap(isWordWrap)
                                        setEditable(!isReadOnly)
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

                                        setText(loadedText)

                                        text.addContentListener(object : ContentListener {
                                            override fun beforeReplace(content: Content) {}
                                            override fun afterInsert(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, insertedContent: CharSequence) {
                                                lineCount = content.lineCount
                                                charCount = content.length
                                            }
                                            override fun afterDelete(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deletedContent: CharSequence) {
                                                lineCount = content.lineCount
                                                charCount = content.length
                                            }
                                        })

                                        // Subscribe to asynchronous search results
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
                                    }
                                },
                                update = { editor ->
                                    editor.setWordwrap(isWordWrap)
                                    editor.setEditable(!isReadOnly)
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom Action Bar: [ 外部应用编辑 ] + [ 保存修改 ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { openInExternalEditor() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.statusInfoBg,
                                contentColor = AppTheme.colors.statusInfoText
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "External App",
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "外部应用编辑",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (saveConfigToDisk(showToast = true)) {
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.brandPrimary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "保存修改",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Line Jump Dialog
    if (showLineJumpDialog) {
        AlertDialog(
            onDismissRequest = { showLineJumpDialog = false },
            title = { Text("跳转到行号", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "当前文件共 $lineCount 行，请输入要跳转的目标行：",
                        fontSize = 13.sp,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = jumpLineInput,
                        onValueChange = { jumpLineInput = it.filter { ch -> ch.isDigit() } },
                        placeholder = { Text("例如 100") },
                        singleLine = true,
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val line = jumpLineInput.toIntOrNull()
                        if (line != null && line in 1..lineCount) {
                            val target = (line - 1).coerceAtLeast(0)
                            val ed = editorView
                            if (ed != null) {
                                ed.jumpToLine(target)
                                scrollToTargetPosition(ed)
                            }
                            showLineJumpDialog = false
                        } else {
                            Toast.makeText(context, "请输入 1 到 $lineCount 之间的有效行号", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary)
                ) {
                    Text("跳转")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLineJumpDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
