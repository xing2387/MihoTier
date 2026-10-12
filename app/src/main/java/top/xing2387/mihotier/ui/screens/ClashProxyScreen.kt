package top.xing2387.mihotier.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.xing2387.mihotier.R
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.ui.theme.ClashPrimary
import top.xing2387.mihotier.ui.viewmodel.ClashProxyIntent
import top.xing2387.mihotier.ui.viewmodel.ClashProxyViewModel

enum class SortOrder { Default, Name, Delay }
enum class ProxyMode { Unchanged, Direct, Global, Rule }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashProxyScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClashProxyViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // UI States for menus & dialogs
    var activeMenu by remember { mutableStateOf("NONE") }
    var showModeDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Show error toast if there's an error
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.handleIntent(ClashProxyIntent.ClearError)
        }
    }

    // Apply filtering and sorting using current group nodes
    val displayNodes = state.currentNodes
        .filter { !state.hideTimeoutNodes || it.latency != 0 }
        .sortedWith { a, b ->
            when (state.sortOrder) {
                SortOrder.Default -> a.defaultOrder.compareTo(b.defaultOrder)
                SortOrder.Name -> a.name.compareTo(b.name, ignoreCase = true)
                SortOrder.Delay -> {
                    val delayA = if (a.latency != null && a.latency > 0) a.latency else 999999
                    val delayB = if (b.latency != null && b.latency > 0) b.latency else 999999
                    delayA.compareTo(delayB)
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxy", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // 1. "规则" 按钮
                    TextButton(
                        onClick = { showRulesDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "规则",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 2. 当前模式文本（Global / Rule / Direct），点击弹出切换模式弹窗
                    val modeLabel = when (state.proxyMode) {
                        ProxyMode.Global -> "Global"
                        ProxyMode.Rule -> "Rule"
                        ProxyMode.Direct -> "Direct"
                        ProxyMode.Unchanged -> "Rule"
                    }
                    TextButton(
                        onClick = { showModeDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = modeLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClashPrimary
                        )
                    }

                    // 3. 闪电测速图标（SVG）
                    IconButton(onClick = { viewModel.handleIntent(ClashProxyIntent.TestLatencies) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bolt),
                            contentDescription = "Test Latencies",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 4. 更多菜单
                    Box {
                        IconButton(onClick = { activeMenu = "MAIN" }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        
                        // Main Menu
                        DropdownMenu(
                            expanded = activeMenu == "MAIN",
                            onDismissRequest = { activeMenu = "NONE" }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Filter") },
                                onClick = { activeMenu = "FILTER" },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowRight, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Mode") },
                                onClick = { activeMenu = "MODE" },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowRight, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Layout") },
                                onClick = { activeMenu = "LAYOUT" },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowRight, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort") },
                                onClick = { activeMenu = "SORT" },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowRight, null) }
                            )
                        }

                        // Filter Submenu
                        DropdownMenu(
                            expanded = activeMenu == "FILTER",
                            onDismissRequest = { activeMenu = "NONE" }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Hide timeout nodes") },
                                onClick = { 
                                    viewModel.handleIntent(ClashProxyIntent.UpdateHideTimeoutNodes(!state.hideTimeoutNodes))
                                    activeMenu = "NONE"
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = state.hideTimeoutNodes,
                                        onCheckedChange = null // Handled by MenuItem click
                                    )
                                }
                            )
                        }

                        // Mode Submenu
                        DropdownMenu(
                            expanded = activeMenu == "MODE",
                            onDismissRequest = { activeMenu = "NONE" }
                        ) {
                            val modes = listOf<Pair<ProxyMode, String>>(ProxyMode.Unchanged to "Unchanged", ProxyMode.Direct to "Direct", ProxyMode.Global to "Global", ProxyMode.Rule to "Rule")
                            modes.forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        viewModel.handleIntent(ClashProxyIntent.UpdateProxyMode(mode))
                                        activeMenu = "NONE"
                                    },
                                    trailingIcon = {
                                        if (state.proxyMode == mode) Icon(Icons.Default.Check, null)
                                    }
                                )
                            }
                        }

                        // Layout Submenu
                        DropdownMenu(
                            expanded = activeMenu == "LAYOUT",
                            onDismissRequest = { activeMenu = "NONE" }
                        ) {
                            val layouts = listOf<Pair<Int, String>>(1 to "One column", 2 to "Two columns", 3 to "Three columns")
                            layouts.forEach { (cols, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        viewModel.handleIntent(ClashProxyIntent.UpdateLayoutColumns(cols))
                                        activeMenu = "NONE"
                                    },
                                    trailingIcon = {
                                        if (state.layoutColumns == cols) Icon(Icons.Default.Check, null)
                                    }
                                )
                            }
                        }

                        // Sort Submenu
                        DropdownMenu(
                            expanded = activeMenu == "SORT",
                            onDismissRequest = { activeMenu = "NONE" }
                        ) {
                            val sorts = listOf<Pair<SortOrder, String>>(SortOrder.Default to "Default", SortOrder.Name to "Name", SortOrder.Delay to "Delay")
                            sorts.forEach { (sort, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        viewModel.handleIntent(ClashProxyIntent.UpdateSortOrder(sort))
                                        activeMenu = "NONE"
                                    },
                                    trailingIcon = {
                                        if (state.sortOrder == sort) Icon(Icons.Default.Check, null)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.handleIntent(ClashProxyIntent.TestLatencies) },
                containerColor = ClashPrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bolt),
                    contentDescription = "Test Latencies",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val groups = state.groups
            val selectedIndex = groups.indexOfFirst { it.name == state.selectedGroupName }.coerceAtLeast(0)

            if (groups.isNotEmpty()) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 16.dp,
                    divider = {} 
                ) {
                    groups.forEachIndexed { index, group ->
                        Tab(
                            selected = selectedIndex == index,
                            onClick = { 
                                viewModel.handleIntent(ClashProxyIntent.SelectGroup(group.name))
                            },
                            text = {
                                Text(
                                    text = group.name,
                                    color = if (selectedIndex == index) ClashPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (displayNodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.isLoading) "Loading proxies..." else "No nodes found in this group",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.layoutColumns),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayNodes) { node ->
                        ProxyNodeCard(node, onClick = {
                            viewModel.handleIntent(ClashProxyIntent.SelectProxy(node))
                        })
                    }
                }
            }
        }
    }

    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("切换代理模式", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        ProxyMode.Rule to "Rule (规则分流)",
                        ProxyMode.Global to "Global (全局代理)",
                        ProxyMode.Direct to "Direct (直接连接)"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.handleIntent(ClashProxyIntent.UpdateProxyMode(mode))
                                    showModeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (state.proxyMode == mode),
                                onClick = {
                                    viewModel.handleIntent(ClashProxyIntent.UpdateProxyMode(mode))
                                    showModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRulesDialog) {
        val yamlText = remember(state.proxyMode) {
            try {
                ConfigManager.getConfig(context)
            } catch (e: Exception) {
                ""
            }
        }
        val rulesList = remember(yamlText) {
            extractRulesFromYaml(yamlText)
        }

        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = {
                Text("规则列表 (${rulesList.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                if (rulesList.isEmpty()) {
                    Text("配置中未找到规则", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        LazyColumn {
                            items(rulesList) { rule ->
                                Text(
                                    text = rule,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRulesDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

data class ProxyNode(val name: String, val type: String, val isActive: Boolean, val latency: Int? = null, val defaultOrder: Int = 0, val groupName: String = "")

@Composable
fun ProxyNodeCard(node: ProxyNode, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (node.isActive) ClashPrimary else MaterialTheme.colorScheme.surface,
            contentColor = if (node.isActive) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                
                // Latency display
                when (node.latency) {
                    -1 -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = if (node.isActive) Color.White else ClashPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                    0 -> {
                        Text("Timeout", fontSize = 12.sp, color = Color(0xFFF44336), fontWeight = FontWeight.Medium)
                    }
                    null -> {} // Not tested yet
                    else -> {
                        val color = when {
                            node.isActive -> Color.White
                            node.latency < 200 -> Color(0xFF4CAF50) // Green
                            node.latency < 500 -> Color(0xFFFF9800) // Orange
                            else -> Color(0xFFF44336) // Red
                        }
                        Text(
                            text = "${node.latency} ms",
                            fontSize = 12.sp,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = node.type,
                fontSize = 12.sp,
                color = if (node.isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}

private fun extractRulesFromYaml(yamlText: String): List<String> {
    val lines = yamlText.lines()
    var inRules = false
    val rules = mutableListOf<String>()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("rules:")) {
            inRules = true
            continue
        }
        if (inRules) {
            if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("\t") && !line.startsWith("-") && trimmed.endsWith(":")) {
                break
            }
            if (trimmed.startsWith("-")) {
                val rule = trimmed.removePrefix("-").trim().trim('\'', '"')
                if (rule.isNotBlank()) rules.add(rule)
            }
        }
    }
    return rules
}

