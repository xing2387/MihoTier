package top.xing2387.mihotier.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.xing2387.mihotier.core.config.NetworkSettingsRepository
import top.xing2387.mihotier.ui.theme.AppTheme

data class AppItem(
    val packageName: String,
    val name: String,
    val isSystemApp: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long
)

enum class AppSortField(val label: String) {
    APP_NAME("应用名称"),
    PACKAGE_NAME("应用包名"),
    INSTALL_TIME("安装时间"),
    UPDATE_TIME("更新时间")
}

private object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(400)
    fun get(packageName: String): ImageBitmap? = cache.get(packageName)
    fun put(packageName: String, bitmap: ImageBitmap) { cache.put(packageName, bitmap) }
}

@Composable
private fun AppIcon(
    packageName: String,
    packageManager: PackageManager,
    modifier: Modifier = Modifier
) {
    var imageBitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (imageBitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val drawable = packageManager.getApplicationIcon(packageName)
                    val bmp = drawable.toBitmap(width = 120, height = 120, config = Bitmap.Config.ARGB_8888).asImageBitmap()
                    AppIconCache.put(packageName, bmp)
                    withContext(Dispatchers.Main) {
                        imageBitmap = bmp
                    }
                } catch (e: Exception) {
                    // Ignore missing or restricted icon
                }
            }
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .background(AppTheme.colors.surfaceVariant, shape = RoundedCornerShape(10.dp))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashAppSelectionScreen(
    isRunning: Boolean = false,
    onRestartVpn: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val packageManager = remember { context.packageManager }

    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var savedPackages by remember {
        mutableStateOf(NetworkSettingsRepository.load(context).accessControlPackages)
    }
    var selectedPackages by remember { mutableStateOf(savedPackages) }

    val hasChanges = (selectedPackages != savedPackages)

    var searchQuery by remember { mutableStateOf("") }
    var filterSystemApps by remember { mutableStateOf(false) }
    var sortField by remember { mutableStateOf(AppSortField.APP_NAME) }
    var isAscending by remember { mutableStateOf(true) }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Load installed apps asynchronously
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val selfPackage = context.packageName
            val installed = try {
                packageManager.getInstalledPackages(0)
            } catch (e: Exception) {
                emptyList()
            }
            val list = installed.mapNotNull { pkg ->
                if (pkg.packageName == selfPackage) return@mapNotNull null
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                val label = try {
                    appInfo.loadLabel(packageManager).toString().ifBlank { pkg.packageName }
                } catch (e: Exception) {
                    pkg.packageName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppItem(
                    packageName = pkg.packageName,
                    name = label,
                    isSystemApp = isSystem,
                    firstInstallTime = pkg.firstInstallTime,
                    lastUpdateTime = pkg.lastUpdateTime
                )
            }
            withContext(Dispatchers.Main) {
                allApps = list
                isLoading = false
            }
        }
    }

    fun doSave() {
        val currentSettings = NetworkSettingsRepository.load(context)
        NetworkSettingsRepository.save(
            context,
            currentSettings.copy(accessControlPackages = selectedPackages)
        )
        savedPackages = selectedPackages
        if (isRunning) {
            onRestartVpn()
            Toast.makeText(context, "分应用设置已保存，正在重启代理", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "分应用设置已保存", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleBack() {
        if (hasChanges) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = hasChanges) {
        handleBack()
    }

    // Filter and sort apps
    val displayedApps = remember(allApps, searchQuery, filterSystemApps, sortField, isAscending) {
        var stream = allApps.asSequence()
        if (filterSystemApps) {
            stream = stream.filter { !it.isSystemApp }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            stream = stream.filter {
                it.name.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }
        val comparator = when (sortField) {
            AppSortField.APP_NAME -> compareBy<AppItem> { it.name.lowercase() }
            AppSortField.PACKAGE_NAME -> compareBy<AppItem> { it.packageName.lowercase() }
            AppSortField.INSTALL_TIME -> compareBy<AppItem> { it.firstInstallTime }
            AppSortField.UPDATE_TIME -> compareBy<AppItem> { it.lastUpdateTime }
        }
        if (isAscending) {
            stream.sortedWith(comparator).toList()
        } else {
            stream.sortedWith(comparator.reversed()).toList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "分应用代理 (Access Control)",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "已选 ${selectedPackages.size} 项 / 共 ${displayedApps.size} 个应用",
                            fontSize = 12.sp,
                            color = colors.textMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { doSave() }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (hasChanges) colors.brandPrimary else colors.textSecondary
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = colors.textPrimary
                            )
                        }

                        // More dropdown menu
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = colors.cardBackground
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "全选当前列表 (Select All)",
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                },
                                onClick = {
                                    val visiblePkgs = displayedApps.map { it.packageName }.toSet()
                                    selectedPackages = selectedPackages + visiblePkgs
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ClearAll,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "清空当前列表 (Clear All)",
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                },
                                onClick = {
                                    val visiblePkgs = displayedApps.map { it.packageName }.toSet()
                                    selectedPackages = selectedPackages - visiblePkgs
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "反选当前列表 (Invert)",
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                },
                                onClick = {
                                    val visiblePkgs = displayedApps.map { it.packageName }.toSet()
                                    val updated = selectedPackages.toMutableSet()
                                    for (pkg in visiblePkgs) {
                                        if (pkg in updated) {
                                            updated.remove(pkg)
                                        } else {
                                            updated.add(pkg)
                                        }
                                    }
                                    selectedPackages = updated
                                    showMoreMenu = false
                                }
                            )
                            HorizontalDivider(color = colors.innerBorder)
                            DropdownMenuItem(
                                leadingIcon = {
                                    Checkbox(
                                        checked = filterSystemApps,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colors.brandPrimary,
                                            uncheckedColor = colors.textMuted
                                        )
                                    )
                                },
                                text = {
                                    Text(
                                        text = "隐藏系统应用 (Filter System)",
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                },
                                onClick = {
                                    filterSystemApps = !filterSystemApps
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "排序方式",
                                            fontSize = 14.sp,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        val arrow = if (isAscending) "↑" else "↓"
                                        Text(
                                            text = "${sortField.label} $arrow",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.brandPrimary
                                        )
                                    }
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showSortMenu = true
                                }
                            )
                        }

                        // Sort sub-menu
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = colors.cardBackground
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isAscending) "当前: 升序排列 (点击切换降序 ↑)" else "当前: 降序排列 (点击切换升序 ↓)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.brandPrimary
                                    )
                                },
                                onClick = {
                                    isAscending = !isAscending
                                }
                            )
                            HorizontalDivider(color = colors.innerBorder)
                            listOf(
                                AppSortField.APP_NAME to "应用名称 (App Name)",
                                AppSortField.PACKAGE_NAME to "应用包名 (Package Name)",
                                AppSortField.INSTALL_TIME to "安装时间 (Install Time)",
                                AppSortField.UPDATE_TIME to "更新时间 (Update Time)"
                            ).forEach { (field, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 14.sp,
                                                fontWeight = if (sortField == field) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (sortField == field) colors.brandPrimary else colors.textPrimary
                                            )
                                            if (sortField == field) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = colors.brandPrimary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        sortField = field
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        },
        containerColor = colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Search Bar (left) + Filter System Apps Switch (right), consistent with ClashLogsScreen
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(19.dp),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    color = colors.cardBackground
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索应用名称或包名...",
                                    fontSize = 13.sp,
                                    color = colors.textMuted
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = colors.textPrimary
                                ),
                                cursorBrush = SolidColor(colors.brandPrimary),
                                modifier = Modifier.fillMaxWidth()
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

                // Quick toggle for hiding system apps
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "隐藏系统",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = filterSystemApps,
                        onCheckedChange = { filterSystemApps = it },
                        modifier = Modifier.scale(0.8f),
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

            // App List
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = colors.brandPrimary
                    )
                } else if (displayedApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "未找到匹配的应用" else "暂无可用应用",
                            fontSize = 15.sp,
                            color = colors.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(displayedApps, key = { it.packageName }) { app ->
                            val isSelected = app.packageName in selectedPackages
                            val toggleApp = {
                                selectedPackages = if (isSelected) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(74.dp)
                                    .clickable { toggleApp() },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) colors.brandPrimary else colors.cardBorder
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 14.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        packageName = app.packageName,
                                        packageManager = packageManager,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = app.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (app.isSystemApp) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = colors.statusInfoBg
                                                ) {
                                                    Text(
                                                        text = "系统",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colors.statusInfoText,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = app.packageName,
                                            fontSize = 12.sp,
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { toggleApp() },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colors.brandPrimary,
                                            uncheckedColor = colors.textMuted,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Unsaved changes dialog
        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                containerColor = colors.cardBackground,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "未保存的修改",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "当前分应用代理列表有未保存的更改，是否在离开前保存？",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        doSave()
                        onNavigateBack()
                    }) {
                        Text("保存并退出", color = colors.brandPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showUnsavedDialog = false }) {
                            Text("取消", color = colors.textMuted)
                        }
                        TextButton(onClick = {
                            showUnsavedDialog = false
                            onNavigateBack()
                        }) {
                            Text("不保存", color = colors.statusDangerText)
                        }
                    }
                }
            )
        }
    }
}
