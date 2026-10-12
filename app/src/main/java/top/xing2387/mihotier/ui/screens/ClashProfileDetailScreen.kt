package top.xing2387.mihotier.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.Profile
import top.xing2387.mihotier.core.config.ProfileRepository
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashProfileDetailScreen(
    profileId: String?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isExistingProfile = profileId != null

    var id by remember { mutableStateOf(profileId ?: UUID.randomUUID().toString()) }
    var name by remember { mutableStateOf("") }
    var isSubscription by remember { mutableStateOf(true) }
    var url by remember { mutableStateOf("") }
    var autoUpdate by remember { mutableStateOf("24 小时") }
    var localUri by remember { mutableStateOf<Uri?>(null) }
    var localFileName by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var autoUpdateExpanded by remember { mutableStateOf(false) }
    var showConfigEditorScreen by remember { mutableStateOf(false) }

    val autoUpdateOptions = listOf(
        "24 小时",
        "12 小时",
        "6 小时",
        "72 小时",
        "每次启动 (Every Startup)",
        "不自动更新 (Disabled)"
    )

    // Load existing profile if editing
    LaunchedEffect(profileId) {
        if (profileId != null) {
            val existing = ProfileRepository.getProfile(profileId)
            if (existing != null) {
                name = existing.name
                url = existing.url
                isSubscription = existing.url.isNotBlank()
                autoUpdate = when (existing.autoUpdate) {
                    "Disabled" -> "不自动更新 (Disabled)"
                    "Every Startup" -> "每次启动 (Every Startup)"
                    else -> existing.autoUpdate.ifBlank { "24 小时" }
                }
                localFileName = existing.localFilePath ?: "${existing.name.ifBlank { "local_config" }}.yaml"
                isActive = existing.isActive
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            localUri = uri
            val resolvedName = resolveFileName(context, uri) ?: (uri.lastPathSegment ?: "config.yaml")
            localFileName = resolvedName
            if (name.isBlank()) {
                name = resolvedName.removeSuffix(".yaml").removeSuffix(".yml")
            }
            coroutineScope.launch {
                ConfigManager.copyConfigFromUri(context, uri, id)
            }
            Toast.makeText(context, "已选择文件: $resolvedName", Toast.LENGTH_SHORT).show()
        }
    }

    // Full-screen YAML Config Editor Page
    if (showConfigEditorScreen) {
        val configFile = File(File(context.filesDir, "config"), "$id.yaml")
        val displayFileName = (name.trim().ifBlank { localFileName.ifBlank { "config" } }).let {
            if (it.endsWith(".yaml") || it.endsWith(".yml")) it else "$it.yaml"
        }
        ClashConfigYamlEditorScreen(
            configFile = configFile,
            displayFileName = displayFileName,
            onNavigateBack = { showConfigEditorScreen = false }
        )
        return
    }

    fun saveProfile() {
        val trimmedName = name.trim().ifBlank {
            if (isSubscription) {
                url.substringAfterLast("/").removeSuffix(".yaml").removeSuffix(".yml").ifBlank { "New Profile" }
            } else {
                localFileName.removeSuffix(".yaml").removeSuffix(".yml").ifBlank { "Local Profile" }
            }
        }

        if (isSubscription) {
            if (url.isBlank()) {
                Toast.makeText(context, "请输入订阅链接 (URL)", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            val existingFile = File(File(context.filesDir, "config"), "$id.yaml")
            if (localUri == null && !existingFile.exists()) {
                Toast.makeText(context, "请选择本地配置文件", Toast.LENGTH_SHORT).show()
                return
            }
        }

        isSaving = true
        coroutineScope.launch {
            val existingFile = File(File(context.filesDir, "config"), "$id.yaml")
            val result = if (isSubscription) {
                ConfigManager.downloadAndSaveConfig(context, url.trim(), id)
            } else if (localUri != null && !existingFile.exists()) {
                ConfigManager.copyConfigFromUri(context, localUri!!, id)
            } else {
                Result.success(Unit)
            }

            if (result.isSuccess) {
                val shouldActivate = isActive || ProfileRepository.profiles.value.isEmpty()
                val profile = Profile(
                    id = id,
                    name = trimmedName,
                    url = if (isSubscription) url.trim() else "",
                    autoUpdate = if (isSubscription) autoUpdate else "Disabled",
                    localFilePath = if (!isSubscription) localFileName.ifBlank { "$trimmedName.yaml" } else null,
                    lastUpdated = System.currentTimeMillis(),
                    isActive = shouldActivate
                )
                ProfileRepository.saveProfile(context, profile)
                Toast.makeText(context, "配置已保存并导入", Toast.LENGTH_SHORT).show()
                onNavigateBack()
            } else {
                Toast.makeText(
                    context,
                    "导入失败: ${result.exceptionOrNull()?.message ?: "请检查链接或文件"}",
                    Toast.LENGTH_LONG
                ).show()
            }
            isSaving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isExistingProfile) "编辑配置文件 (Edit Profile)" else "新建配置文件 (New Profile)",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEEF3F8)
                )
            )
        },
        containerColor = Color(0xFFEEF3F8)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
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
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    // 1. 名称 (Name)
                    Text(
                        text = "名称 (Name)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = {
                            Text(
                                text = "请输入配置名称",
                                fontSize = 15.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Normal
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF5B8DEF),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            cursorColor = Color(0xFF104179)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. 类型 (Type)
                    Text(
                        text = "类型 (Type)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFFE2E8F0))
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Option: 订阅 / URL
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSubscription) Color(0xFFBAE6FD) else Color.Transparent
                                )
                                .clickable { isSubscription = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "订阅 / URL",
                                fontSize = 14.sp,
                                fontWeight = if (isSubscription) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSubscription) Color(0xFF0369A1) else Color(0xFF64748B)
                            )
                        }

                        // Right Option: 本地文件 (File)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (!isSubscription) Color(0xFFBAE6FD) else Color.Transparent
                                )
                                .clickable { isSubscription = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "本地文件 (File)",
                                fontSize = 14.sp,
                                fontWeight = if (!isSubscription) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (!isSubscription) Color(0xFF0369A1) else Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. 链接 / 路径 (URL / Path)
                    val configFile = File(File(context.filesDir, "config"), "$id.yaml")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "链接 / 路径 (URL / Path)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )

                        if ((isExistingProfile || localUri != null) && configFile.exists()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFBAE6FD),
                                modifier = Modifier.clickable { showConfigEditorScreen = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit YAML",
                                        tint = Color(0xFF0369A1),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "编辑 YAML",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0369A1)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isSubscription) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            placeholder = {
                                Text(
                                    text = "http://example.com/config.yaml",
                                    fontSize = 15.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Normal
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF5B8DEF),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                cursorColor = Color(0xFF104179)
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Local File Selector Box matching the OutlinedTextField visual style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                                .clickable { filePickerLauncher.launch("*/*") }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (localFileName.isNotBlank()) localFileName else "点击选择本地 YAML 配置文件...",
                                fontSize = 15.sp,
                                color = if (localFileName.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Select File",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. 自动更新 (Auto-Update) (Optional)
                    Text(
                        text = "自动更新 (Auto-Update) (Optional)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSubscription) Color(0xFF0F172A) else Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSubscription) Color(0xFFF8FAFC) else Color(0xFFF1F5F9))
                                .border(
                                    width = 1.dp,
                                    color = if (isSubscription) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = isSubscription) {
                                    autoUpdateExpanded = true
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isSubscription) autoUpdate else "不自动更新 (本地文件)",
                                fontSize = 15.sp,
                                color = if (isSubscription) Color(0xFF0F172A) else Color(0xFF94A3B8)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Auto-Update Interval",
                                tint = if (isSubscription) Color(0xFF475569) else Color(0xFF94A3B8),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = autoUpdateExpanded,
                            onDismissRequest = { autoUpdateExpanded = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color.White,
                            modifier = Modifier.fillMaxWidth(0.82f)
                        ) {
                            autoUpdateOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            fontSize = 15.sp,
                                            fontWeight = if (option == autoUpdate) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (option == autoUpdate) Color(0xFF0369A1) else Color(0xFF0F172A)
                                        )
                                    },
                                    onClick = {
                                        autoUpdate = option
                                        autoUpdateExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // 5. 保存并导入 (Save & Import Button)
                    Button(
                        onClick = { saveProfile() },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF104179),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF104179).copy(alpha = 0.6f),
                            disabledContentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "正在保存并导入...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "保存并导入",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClashConfigYamlEditorScreen(
    configFile: File,
    displayFileName: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var configText by remember {
        mutableStateOf(if (configFile.exists()) configFile.readText() else "")
    }
    var lastSavedModified by remember {
        mutableStateOf(if (configFile.exists()) configFile.lastModified() else 0L)
    }

    // Intercept system back button to return to Profile detail page
    BackHandler(onBack = onNavigateBack)

    // Reload file automatically when returning from an external editor app
    DisposableEffect(lifecycleOwner, configFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && configFile.exists()) {
                val currentModified = configFile.lastModified()
                if (currentModified > lastSavedModified) {
                    configText = configFile.readText()
                    lastSavedModified = currentModified
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun saveConfigToDisk(showToast: Boolean = true): Boolean {
        return try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(configText)
            lastSavedModified = configFile.lastModified()
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
        // Save current buffer first so external app gets the latest text
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

            val editIntent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, "text/plain")
                clipData = ClipData.newRawUri(displayFileName, uri)
                addFlags(flags)
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                clipData = ClipData.newRawUri(displayFileName, uri)
                addFlags(flags)
            }

            val pm = context.packageManager
            val targetIntent = if (editIntent.resolveActivity(pm) != null) editIntent else viewIntent
            val chooser = Intent.createChooser(targetIntent, "选择外部编辑应用").apply {
                addFlags(flags)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "未找到可用的外部编辑应用: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val lineCount = remember(configText) {
        if (configText.isEmpty()) 0 else configText.count { it == '\n' } + 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "编辑配置文件 (YAML)",
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
                    IconButton(onClick = { openInExternalEditor() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in External App",
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    // Header info row: File Name + YAML Badge + Line count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = displayFileName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFBAE6FD)
                            ) {
                                Text(
                                    text = "YAML",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0369A1),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${lineCount} 行 · ${configText.length} 字符",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Full-height YAML Monospace Editor Box
                    OutlinedTextField(
                        value = configText,
                        onValueChange = { configText = it },
                        placeholder = {
                            Text(
                                text = "# 在此输入或粘贴 Clash YAML 配置内容...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF5B8DEF),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            cursorColor = Color(0xFF104179)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Action Buttons: [ 外部应用编辑 ] + [ 保存修改 ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left secondary button: Jump to external editor app
                        Button(
                            onClick = { openInExternalEditor() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFBAE6FD),
                                contentColor = Color(0xFF0369A1)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 1.dp
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External App",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "外部应用编辑",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Right primary button: Save & Return
                        Button(
                            onClick = {
                                if (saveConfigToDisk(showToast = true)) {
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF104179),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 2.dp
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "保存修改",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveFileName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}
