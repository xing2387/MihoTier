package top.xing2387.mihotier.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    private val cache = android.util.LruCache<String, ImageBitmap>(400)
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
                .background(Color(0xFFE2E8F0), shape = RoundedCornerShape(10.dp))
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
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "已选 ${selectedPackages.size} 项 / 共 ${displayedApps.size} 个应用",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { doSave() }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (hasChanges) Color(0xFF1D68BD) else Color(0xFF0F172A)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color(0xFF0F172A)
                            )
                        }

                        // More dropdown menu (White rounded card style)
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color.White
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = null,
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "全选当前列表 (Select All)",
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
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
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "清空当前列表 (Clear All)",
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
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
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "反选当前列表 (Invert)",
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
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
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            DropdownMenuItem(
                                leadingIcon = {
                                    Checkbox(
                                        checked = filterSystemApps,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF1D68BD),
                                            uncheckedColor = Color(0xFF64748B)
                                        )
                                    )
                                },
                                text = {
                                    Text(
                                        text = "隐藏系统应用 (Filter System)",
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
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
                                        tint = Color(0xFF0F172A),
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
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        val arrow = if (isAscending) "↑" else "↓"
                                        Text(
                                            text = "${sortField.label} $arrow",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0369A1)
                                        )
                                    }
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showSortMenu = true
                                }
                            )
                        }

                        // Sort sub-menu (White rounded card style)
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color.White
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isAscending) "当前: 升序排列 (点击切换降序 ↑)" else "当前: 降序排列 (点击切换升序 ↓)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0369A1)
                                    )
                                },
                                onClick = {
                                    isAscending = !isAscending
                                }
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9))
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
                                                color = if (sortField == field) Color(0xFF0369A1) else Color(0xFF0F172A)
                                            )
                                            if (sortField == field) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = Color(0xFF0369A1)
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
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    color = Color.White
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
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索应用名称或包名...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                ),
                                cursorBrush = SolidColor(Color(0xFF1D68BD)),
                                modifier = Modifier.fillMaxWidth()
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

                // Quick toggle for hiding system apps
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "隐藏系统",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = filterSystemApps,
                        onCheckedChange = { filterSystemApps = it },
                        modifier = Modifier.scale(0.8f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1D68BD),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
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
                        color = Color(0xFF1D68BD)
                    )
                } else if (displayedApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "未找到匹配的应用" else "暂无可用应用",
                            fontSize = 15.sp,
                            color = Color(0xFF64748B)
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
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF5B8DEF) else Color(0xFFE2E8F0)
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
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (app.isSystemApp) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFBAE6FD)
                                                ) {
                                                    Text(
                                                        text = "系统",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF0369A1),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = app.packageName,
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { toggleApp() },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF1D68BD),
                                            uncheckedColor = Color(0xFF94A3B8),
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
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "未保存的修改",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                },
                text = {
                    Text(
                        text = "当前分应用代理列表有未保存的更改，是否在离开前保存？",
                        fontSize = 14.sp,
                        color = Color(0xFF475569)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        doSave()
                        onNavigateBack()
                    }) {
                        Text("保存并退出", color = Color(0xFF104179), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showUnsavedDialog = false }) {
                            Text("取消", color = Color(0xFF64748B))
                        }
                        TextButton(onClick = {
                            showUnsavedDialog = false
                            onNavigateBack()
                        }) {
                            Text("不保存", color = Color(0xFFDC2626))
                        }
                    }
                }
            )
        }
    }
}
