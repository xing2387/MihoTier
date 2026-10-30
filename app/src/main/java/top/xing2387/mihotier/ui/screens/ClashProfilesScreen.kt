package top.xing2387.mihotier.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.Profile
import top.xing2387.mihotier.core.config.ProfileRepository
import top.xing2387.mihotier.core.vpn.UnifiedVpnService
import top.xing2387.mihotier.ui.theme.AppTheme
import top.xing2387.mihotier.ui.utils.QrCodeUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashProfilesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String?) -> Unit
) {
    val context = LocalContext.current
    val profiles by ProfileRepository.profiles.collectAsState()

    // Refresh repository when entering this screen
    LaunchedEffect(Unit) {
        ProfileRepository.init(context)
    }

    val coroutineScope = rememberCoroutineScope()
    var showShareChoiceDialog by remember { mutableStateOf(false) }
    var profileToShare by remember { mutableStateOf<Profile?>(null) }
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var profileForQrCode by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "配置文件 (Profiles)",
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
                    IconButton(onClick = {
                        ProfileRepository.init(context)
                        val remoteProfiles = profiles.filter { it.url.isNotBlank() }
                        if (remoteProfiles.isEmpty()) {
                            Toast.makeText(context, "列表已刷新", Toast.LENGTH_SHORT).show()
                        } else {
                            coroutineScope.launch {
                                Toast.makeText(context, "正在更新订阅配置...", Toast.LENGTH_SHORT).show()
                                var successCount = 0
                                var activeUpdated = false
                                for (prof in remoteProfiles) {
                                    val res = ConfigManager.downloadAndSaveConfig(context, prof.url, prof.id)
                                    if (res.isSuccess) {
                                        if (prof.isActive) activeUpdated = true
                                        ProfileRepository.saveProfile(
                                            context,
                                            prof.copy(lastUpdated = System.currentTimeMillis()),
                                            reloadIfActive = false
                                        )
                                        successCount++
                                    }
                                }
                                if (activeUpdated) {
                                    UnifiedVpnService.reloadIfRunning(context)
                                }
                                Toast.makeText(
                                    context,
                                    "已完成更新 ($successCount/${remoteProfiles.size})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                    IconButton(onClick = { onNavigateToDetail(null) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.background
                )
            )
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isSubscription = profile.url.isNotBlank()
                val displaySubtitle = if (isSubscription) {
                    profile.url
                } else if (!profile.localFilePath.isNullOrBlank()) {
                    profile.localFilePath!!
                } else {
                    val n = profile.name.ifBlank { "local_config" }
                    if (n.endsWith(".yaml") || n.endsWith(".yml")) n else "$n.yaml"
                }

                ProfileCard(
                    name = profile.name,
                    subtitle = displaySubtitle,
                    isSubscription = isSubscription,
                    timeAgo = formatTimeAgo(profile.lastUpdated),
                    isActive = profile.isActive,
                    onCardClick = {
                        if (!profile.isActive) {
                            ProfileRepository.setActiveProfile(context, profile.id)
                            Toast.makeText(context, "已切换配置: ${profile.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onUpdateClick = {
                        if (profile.url.isBlank()) {
                            Toast.makeText(context, "本地配置无需通过订阅链接更新", Toast.LENGTH_SHORT).show()
                        } else {
                            coroutineScope.launch {
                                Toast.makeText(context, "正在更新 ${profile.name}...", Toast.LENGTH_SHORT).show()
                                val result = ConfigManager.downloadAndSaveConfig(context, profile.url, profile.id)
                                if (result.isSuccess) {
                                    val updated = profile.copy(lastUpdated = System.currentTimeMillis())
                                    ProfileRepository.saveProfile(context, updated)
                                    Toast.makeText(context, "更新成功", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "更新失败: ${result.exceptionOrNull()?.message ?: "未知错误"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    onEditClick = {
                        onNavigateToDetail(profile.id)
                    },
                    onShareClick = {
                        if (profile.url.isNotBlank()) {
                            profileToShare = profile
                            showShareChoiceDialog = true
                        } else {
                            shareProfileFile(context, profile)
                        }
                    },
                    onDeleteClick = {
                        ProfileRepository.deleteProfile(context, profile.id)
                    }
                )
            }
        }

        if (showShareChoiceDialog && profileToShare != null) {
            val targetProfile = profileToShare!!
            AlertDialog(
                onDismissRequest = { showShareChoiceDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = AppTheme.colors.cardBackground,
                title = {
                    Text(
                        text = "分享配置",
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "选择分享 \"${targetProfile.name}\" 的方式：",
                            fontSize = 13.sp,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Option 1: File
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareChoiceDialog = false
                                    shareProfileFile(context, targetProfile)
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = AppTheme.colors.surfaceSubtle,
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppTheme.colors.brandPrimary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "分享文件",
                                            tint = AppTheme.colors.brandPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "分享文件",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "导出并分享配置文件 (.yaml)",
                                        fontSize = 12.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        }

                        // Option 2: URL
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareChoiceDialog = false
                                    shareProfileUrl(context, targetProfile.url)
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = AppTheme.colors.surfaceSubtle,
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppTheme.colors.brandPrimary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = "分享链接",
                                            tint = AppTheme.colors.brandPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "分享链接",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = targetProfile.url,
                                        fontSize = 12.sp,
                                        color = AppTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Option 3: QR Code
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val prof = targetProfile
                                    showShareChoiceDialog = false
                                    profileForQrCode = prof
                                    showQrCodeDialog = true
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = AppTheme.colors.surfaceSubtle,
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppTheme.colors.brandPrimary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.QrCode,
                                            contentDescription = "二维码",
                                            tint = AppTheme.colors.brandPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "二维码",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "展示订阅链接二维码，方便扫码导入",
                                        fontSize = 12.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showShareChoiceDialog = false }) {
                        Text("取消", color = AppTheme.colors.brandPrimary)
                    }
                }
            )
        }

        if (showQrCodeDialog && profileForQrCode != null) {
            val qrProfile = profileForQrCode!!
            val qrBitmap = remember(qrProfile.url) {
                QrCodeUtils.generateQrCodeBitmap(qrProfile.url, sizePx = 700)
            }

            AlertDialog(
                onDismissRequest = { showQrCodeDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = AppTheme.colors.cardBackground,
                title = {
                    Column {
                        Text(
                            text = "配置二维码",
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = qrProfile.name,
                            fontSize = 13.sp,
                            color = AppTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // QR Code container - pure white card so dark mode doesn't invert QR code contrast
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "订阅二维码",
                                    modifier = Modifier
                                        .size(230.dp)
                                        .padding(14.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(230.dp)
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "生成二维码失败",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // URL display and copy box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AppTheme.colors.surfaceSubtle,
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", qrProfile.url))
                                    Toast.makeText(context, "已复制链接到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = qrProfile.url,
                                    fontSize = 11.sp,
                                    color = AppTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "复制链接",
                                    tint = AppTheme.colors.brandPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("URL", qrProfile.url))
                            Toast.makeText(context, "已复制链接到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("复制链接", color = AppTheme.colors.brandPrimary, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQrCodeDialog = false }) {
                        Text("关闭", color = AppTheme.colors.textSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileCard(
    name: String,
    subtitle: String,
    isSubscription: Boolean,
    timeAgo: String,
    isActive: Boolean,
    onCardClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.colors.cardBackground
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) AppTheme.colors.brandPrimary else AppTheme.colors.cardBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isActive,
                onClick = onCardClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AppTheme.colors.brandPrimary,
                    unselectedColor = AppTheme.colors.textSecondary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Line 1: Profile Name + Light-blue Badge ("订阅" / "本地")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppTheme.colors.statusInfoBg
                    ) {
                        Text(
                            text = if (isSubscription) "订阅" else "本地",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.statusInfoText,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Line 2: URL or local filename
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Uniform top margin above update time text (NO icon before text)
                Spacer(modifier = Modifier.height(6.dp))

                // Line 3: Update time text
                Text(
                    text = timeAgo,
                    fontSize = 12.sp,
                    color = AppTheme.colors.textMuted,
                    maxLines = 1
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = AppTheme.colors.textPrimary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    offset = DpOffset(x = 0.dp, y = 0.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = AppTheme.colors.cardBackground
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "更新",
                                fontSize = 15.sp,
                                color = AppTheme.colors.textPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "更新",
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onUpdateClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "编辑",
                                fontSize = 15.sp,
                                color = AppTheme.colors.textPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEditClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "分享",
                                fontSize = 15.sp,
                                color = AppTheme.colors.textPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "分享",
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onShareClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "删除",
                                fontSize = 15.sp,
                                color = Color(0xFFDC2626)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

private fun shareProfileFile(context: Context, profile: Profile) {
    val configFile = File(File(context.filesDir, "config"), "${profile.id}.yaml")
    if (!configFile.exists()) {
        Toast.makeText(context, "配置文件不存在", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val sanitizedName = profile.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "profile" }
        val shareDir = File(context.cacheDir, "shared_profiles")
        if (!shareDir.exists()) shareDir.mkdirs()
        val shareFile = File(shareDir, "$sanitizedName.yaml")
        configFile.copyTo(shareFile, overwrite = true)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(profile.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "分享配置文件").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "分享文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareProfileUrl(context: Context, url: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(sendIntent, "分享订阅链接").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "分享链接失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    if (timestamp <= 0L) return "从未更新"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}天前更新"
        hours > 0 -> "${hours}小时前更新"
        minutes > 0 -> "${minutes}分钟前更新"
        else -> "刚刚更新"
    }
}
