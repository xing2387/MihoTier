package top.xing2387.mihotier.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "配置文件 (Profiles)",
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
                    IconButton(onClick = {
                        ProfileRepository.init(context)
                        val remoteProfiles = profiles.filter { it.url.isNotBlank() }
                        if (remoteProfiles.isEmpty()) {
                            Toast.makeText(context, "列表已刷新", Toast.LENGTH_SHORT).show()
                        } else {
                            coroutineScope.launch {
                                Toast.makeText(context, "正在更新订阅配置...", Toast.LENGTH_SHORT).show()
                                var successCount = 0
                                for (prof in remoteProfiles) {
                                    val res = ConfigManager.downloadAndSaveConfig(context, prof.url, prof.id)
                                    if (res.isSuccess) {
                                        ProfileRepository.saveProfile(
                                            context,
                                            prof.copy(lastUpdated = System.currentTimeMillis())
                                        )
                                        successCount++
                                    }
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
                            tint = Color(0xFF0F172A)
                        )
                    }
                    IconButton(onClick = { onNavigateToDetail(null) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
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
                        ProfileRepository.setActiveProfile(context, profile.id)
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
                title = { Text("Share Profile", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Choose how to share \"${targetProfile.name}\":",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareChoiceDialog = false
                                    shareProfileFile(context, targetProfile)
                                }
                                .padding(vertical = 10.dp),
                            color = Color.Transparent
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Share File",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Share File", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "Share configuration file (.yaml)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareChoiceDialog = false
                                    shareProfileUrl(context, targetProfile.url)
                                }
                                .padding(vertical = 10.dp),
                            color = Color.Transparent
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Share URL",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Share URL", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        targetProfile.url,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showShareChoiceDialog = false }) {
                        Text("Cancel")
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
            containerColor = Color.White
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) Color(0xFF5B8DEF) else Color(0xFFE2E8F0)
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
                    selectedColor = Color(0xFF1D68BD),
                    unselectedColor = Color(0xFF64748B)
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
                            text = if (isSubscription) "订阅" else "本地",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0369A1),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Line 2: URL or local filename
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Uniform top margin above update time text (NO icon before text)
                Spacer(modifier = Modifier.height(6.dp))

                // Line 3: Update time text
                Text(
                    text = timeAgo,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color(0xFF0F172A)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    offset = DpOffset(x = 0.dp, y = 0.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Update",
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Update",
                                tint = Color(0xFF0F172A),
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
                                text = "Edit",
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color(0xFF0F172A),
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
                                text = "Share",
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Share",
                                tint = Color(0xFF0F172A),
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
                                text = "Delete",
                                fontSize = 15.sp,
                                color = Color(0xFFDC2626)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
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
        Toast.makeText(context, "Config file does not exist", Toast.LENGTH_SHORT).show()
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
        val chooser = Intent.createChooser(sendIntent, "Share Profile File").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareProfileUrl(context: Context, url: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(sendIntent, "Share URL").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share URL: ${e.message}", Toast.LENGTH_SHORT).show()
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
