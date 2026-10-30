package top.xing2387.mihotier.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.Profile
import top.xing2387.mihotier.core.config.ProfileRepository
import top.xing2387.mihotier.ui.theme.AppTheme
import top.xing2387.mihotier.ui.theme.appTextFieldColors
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashProfileDetailScreen(
    profileId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit = {},
    navController: NavController? = null
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

    // Listen to QR Code scan result from ClashQrScannerScreen
    val scannedContentFlow = navController?.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("scanned_qr_content", null)
    val scannedContent by scannedContentFlow?.collectAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(scannedContent) {
        val raw = scannedContent
        if (!raw.isNullOrBlank()) {
            navController?.currentBackStackEntry?.savedStateHandle?.remove<String>("scanned_qr_content")

            var parsedUrl = raw.trim()
            var parsedName: String? = null

            // Support clash://install-config?url=...&name=... and clashmeta://install-config?url=...
            if (parsedUrl.startsWith("clash://install-config", ignoreCase = true) ||
                parsedUrl.startsWith("clashmeta://install-config", ignoreCase = true)) {
                try {
                    val uri = Uri.parse(parsedUrl)
                    uri.getQueryParameter("url")?.let { parsedUrl = it }
                    uri.getQueryParameter("name")?.let { parsedName = it }
                } catch (_: Exception) {}
            }

            // Extract name from URL anchor #Name if present
            if (parsedName == null && parsedUrl.contains("#")) {
                val anchor = parsedUrl.substringAfterLast("#")
                if (anchor.isNotBlank()) {
                    parsedName = Uri.decode(anchor)
                }
            }

            if (parsedUrl.startsWith("http://", ignoreCase = true) || parsedUrl.startsWith("https://", ignoreCase = true)) {
                isSubscription = true
                url = parsedUrl
                if (!parsedName.isNullOrBlank() && name.isBlank()) {
                    name = parsedName!!
                } else if (name.isBlank()) {
                    try {
                        name = Uri.parse(parsedUrl).host ?: "Subscription"
                    } catch (_: Exception) {
                        name = "Subscription"
                    }
                }
                Toast.makeText(context, "已扫码填充订阅链接", Toast.LENGTH_SHORT).show()
            } else {
                isSubscription = true
                url = parsedUrl
                Toast.makeText(context, "已扫码填充内容", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                        color = AppTheme.colors.textPrimary
                    )
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
                    if (!isExistingProfile) {
                        IconButton(onClick = onNavigateToScanner) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "扫码导入",
                                tint = AppTheme.colors.textPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.background
                )
            )
        },
        containerColor = AppTheme.colors.background
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
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
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
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = {
                            Text(
                                text = "请输入配置名称",
                                fontSize = 15.sp,
                                color = AppTheme.colors.textMuted
                            )
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.Normal
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = appTextFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. 类型 (Type)
                    Text(
                        text = "类型 (Type)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(AppTheme.colors.surfaceVariant)
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
                                    if (isSubscription) AppTheme.colors.tabSelectedBg else Color.Transparent
                                )
                                .clickable { isSubscription = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "订阅 / URL",
                                fontSize = 14.sp,
                                fontWeight = if (isSubscription) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSubscription) AppTheme.colors.tabSelectedText else AppTheme.colors.tabUnselectedText
                            )
                        }

                        // Right Option: 本地文件 (File)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (!isSubscription) AppTheme.colors.tabSelectedBg else Color.Transparent
                                )
                                .clickable { isSubscription = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "本地文件 (File)",
                                fontSize = 14.sp,
                                fontWeight = if (!isSubscription) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (!isSubscription) AppTheme.colors.tabSelectedText else AppTheme.colors.tabUnselectedText
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
                            color = AppTheme.colors.textPrimary
                        )

                        if ((isExistingProfile || localUri != null) && configFile.exists()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppTheme.colors.statusInfoBg,
                                modifier = Modifier.clickable { showConfigEditorScreen = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit YAML",
                                        tint = AppTheme.colors.statusInfoText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "编辑配置文件",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.statusInfoText
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
                                    color = AppTheme.colors.textMuted
                                )
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = AppTheme.colors.textPrimary,
                                fontWeight = FontWeight.Normal
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = appTextFieldColors(),
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
                                .background(AppTheme.colors.surfaceSubtle)
                                .border(1.dp, AppTheme.colors.innerBorder, RoundedCornerShape(12.dp))
                                .clickable { filePickerLauncher.launch("*/*") }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (localFileName.isNotBlank()) localFileName else "点击选择本地 YAML 配置文件...",
                                fontSize = 15.sp,
                                color = if (localFileName.isNotBlank()) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Select File",
                                tint = AppTheme.colors.textSecondary,
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
                        color = if (isSubscription) AppTheme.colors.textPrimary else AppTheme.colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSubscription) AppTheme.colors.surfaceSubtle else AppTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSubscription) AppTheme.colors.innerBorder else AppTheme.colors.cardBorder,
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
                                color = if (isSubscription) AppTheme.colors.textPrimary else AppTheme.colors.textMuted
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Auto-Update Interval",
                                tint = if (isSubscription) AppTheme.colors.textSecondary else AppTheme.colors.textMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = autoUpdateExpanded,
                            onDismissRequest = { autoUpdateExpanded = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = AppTheme.colors.cardBackground,
                            modifier = Modifier.fillMaxWidth(0.82f)
                        ) {
                            autoUpdateOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            fontSize = 15.sp,
                                            fontWeight = if (option == autoUpdate) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (option == autoUpdate) AppTheme.colors.brandPrimary else AppTheme.colors.textPrimary
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
                            containerColor = AppTheme.colors.brandPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = AppTheme.colors.brandPrimary.copy(alpha = 0.6f),
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
