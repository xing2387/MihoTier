package top.xing2387.mihotier.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.ui.screens.ProxyMode
import top.xing2387.mihotier.ui.screens.ProxyNode
import top.xing2387.mihotier.ui.screens.SortOrder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ProxyGroup(
    val name: String,
    val type: String,
    val now: String,
    val all: List<String>
)

data class ProxyDetail(
    val type: String,
    val latency: Int?
)

data class ClashProxyState(
    val groups: List<ProxyGroup> = emptyList(),
    val selectedGroupName: String = "",
    val allProxiesDetails: Map<String, ProxyDetail> = emptyMap(),
    val hideTimeoutNodes: Boolean = false,
    val proxyMode: ProxyMode = ProxyMode.Unchanged,
    val layoutColumns: Int = 2,
    val sortOrder: SortOrder = SortOrder.Default,
    val error: String? = null,
    val isLoading: Boolean = false
) {
    val currentGroup: ProxyGroup?
        get() = groups.find { it.name == selectedGroupName } ?: groups.firstOrNull()

    val currentNodes: List<ProxyNode>
        get() {
            val group = currentGroup ?: return emptyList()
            return group.all.mapIndexed { index, nodeName ->
                val detail = allProxiesDetails[nodeName]
                ProxyNode(
                    name = nodeName,
                    type = detail?.type ?: "Unknown",
                    isActive = (nodeName == group.now),
                    latency = detail?.latency,
                    defaultOrder = index,
                    groupName = group.name
                )
            }
        }
}

sealed class ClashProxyIntent {
    object FetchProxies : ClashProxyIntent()
    object TestLatencies : ClashProxyIntent()
    data class SelectGroup(val groupName: String) : ClashProxyIntent()
    data class SelectProxy(val node: ProxyNode) : ClashProxyIntent()
    data class UpdateHideTimeoutNodes(val hide: Boolean) : ClashProxyIntent()
    data class UpdateProxyMode(val mode: ProxyMode) : ClashProxyIntent()
    data class UpdateLayoutColumns(val columns: Int) : ClashProxyIntent()
    data class UpdateSortOrder(val order: SortOrder) : ClashProxyIntent()
    object ClearError : ClashProxyIntent()
}

class ClashProxyViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ClashProxyState())
    val state: StateFlow<ClashProxyState> = _state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    init {
        handleIntent(ClashProxyIntent.FetchProxies)
    }

    fun handleIntent(intent: ClashProxyIntent) {
        when (intent) {
            is ClashProxyIntent.FetchProxies -> fetchProxies()
            is ClashProxyIntent.TestLatencies -> testLatencies()
            is ClashProxyIntent.SelectGroup -> _state.update { it.copy(selectedGroupName = intent.groupName) }
            is ClashProxyIntent.SelectProxy -> selectProxy(intent.node)
            is ClashProxyIntent.UpdateHideTimeoutNodes -> _state.update { it.copy(hideTimeoutNodes = intent.hide) }
            is ClashProxyIntent.UpdateProxyMode -> updateProxyMode(intent.mode)
            is ClashProxyIntent.UpdateLayoutColumns -> _state.update { it.copy(layoutColumns = intent.columns) }
            is ClashProxyIntent.UpdateSortOrder -> _state.update { it.copy(sortOrder = intent.order) }
            is ClashProxyIntent.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    private fun parseGroupNamesFromYaml(yamlText: String): List<String> {
        val groupNames = mutableListOf<String>()
        var inProxyGroups = false
        for (line in yamlText.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("proxy-groups:")) {
                inProxyGroups = true
                continue
            }
            if (inProxyGroups) {
                if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("\t") && !line.startsWith("-") && trimmed.endsWith(":")) {
                    inProxyGroups = false
                    continue
                }
                if (trimmed.startsWith("name:")) {
                    val name = trimmed.substringAfter("name:").trim().trim('\'', '"')
                    if (name.isNotBlank() && !groupNames.contains(name)) {
                        groupNames.add(name)
                    }
                } else if (trimmed.startsWith("-") && trimmed.contains("name:")) {
                    val name = trimmed.substringAfter("name:").trim().trim('\'', '"')
                    if (name.isNotBlank() && !groupNames.contains(name)) {
                        groupNames.add(name)
                    }
                }
            }
        }
        return groupNames
    }

    private fun fetchProxies(silently: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!silently) {
                _state.update { it.copy(isLoading = true) }
            }
            try {
                // 1. Fetch current config to check active mode
                try {
                    val configReq = Request.Builder()
                        .url("http://127.0.0.1:9090/configs")
                        .build()
                    client.newCall(configReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (body != null) {
                                val cfgJson = JSONObject(body)
                                val rawModeStr = cfgJson.optString("mode", "").lowercase()
                                val app = getApplication<Application>()
                                val modeStr = if (top.xing2387.mihotier.core.config.EasyTierRepository.getBridgeRoutingInfo(app).enabled &&
                                    ConfigManager.getConfiguredClashMode(app) == "global"
                                ) {
                                    "global"
                                } else {
                                    rawModeStr
                                }
                                val mode = when (modeStr) {
                                    "direct" -> ProxyMode.Direct
                                    "global" -> ProxyMode.Global
                                    "rule" -> ProxyMode.Rule
                                    else -> ProxyMode.Unchanged
                                }
                                _state.update { it.copy(proxyMode = mode) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore config fetch error
                }

                // 2. Fetch proxies
                val request = Request.Builder()
                    .url("http://127.0.0.1:9090/proxies")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val proxiesObj = JSONObject(responseBody).getJSONObject("proxies")
                            val discoveredGroups = mutableMapOf<String, ProxyGroup>()
                            val proxiesDetails = mutableMapOf<String, ProxyDetail>()

                            val keys = proxiesObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val obj = proxiesObj.getJSONObject(key)
                                val name = obj.optString("name", key)
                                val type = obj.optString("type", "Unknown")

                                val history = obj.optJSONArray("history")
                                val latency = if (history != null && history.length() > 0) {
                                    val last = history.getJSONObject(history.length() - 1)
                                    last.optInt("delay", 0)
                                } else null

                                proxiesDetails[name] = ProxyDetail(type = type, latency = latency)

                                val allArray = obj.optJSONArray("all")
                                if (allArray != null) {
                                    val allList = mutableListOf<String>()
                                    for (i in 0 until allArray.length()) {
                                        allList.add(allArray.getString(i))
                                    }
                                    discoveredGroups[name] = ProxyGroup(
                                        name = name,
                                        type = type,
                                        now = obj.optString("now", ""),
                                        all = allList
                                    )
                                }
                            }

                            // Read YAML config to order groups by their appearance in proxy-groups
                            val orderedGroupNames = mutableListOf<String>()
                            val yamlText = try {
                                ConfigManager.getConfig(getApplication())
                            } catch (e: Exception) {
                                ""
                            }
                            val yamlGroups = parseGroupNamesFromYaml(yamlText)
                            for (g in yamlGroups) {
                                if (discoveredGroups.containsKey(g) && !orderedGroupNames.contains(g)) {
                                    orderedGroupNames.add(g)
                                }
                            }
                            // Add any other discovered groups (e.g. GLOBAL)
                            for (g in discoveredGroups.keys) {
                                if (!orderedGroupNames.contains(g)) {
                                    orderedGroupNames.add(g)
                                }
                            }

                            val finalGroups = orderedGroupNames.mapNotNull { discoveredGroups[it] }
                            val currentSelected = _state.value.selectedGroupName
                            val newSelectedGroup = if (currentSelected in orderedGroupNames) {
                                currentSelected
                            } else {
                                finalGroups.firstOrNull()?.name ?: ""
                            }

                            _state.update {
                                it.copy(
                                    groups = finalGroups,
                                    selectedGroupName = newSelectedGroup,
                                    allProxiesDetails = proxiesDetails,
                                    isLoading = false
                                )
                            }
                        } else {
                            _state.update { it.copy(error = "Empty response", isLoading = false) }
                        }
                    } else {
                        _state.update { it.copy(error = "Failed to fetch proxies: ${response.code}", isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Error: ${e.message}", isLoading = false) }
            }
        }
    }

    private fun testLatencies() {
        viewModelScope.launch {
            val currentNodes = _state.value.currentNodes
            if (currentNodes.isEmpty()) return@launch

            // Set latency to -1 to trigger loading spinner
            _state.update { currentState ->
                val updatedDetails = currentState.allProxiesDetails.toMutableMap()
                for (node in currentNodes) {
                    val existing = updatedDetails[node.name]
                    updatedDetails[node.name] = (existing ?: ProxyDetail(node.type, null)).copy(latency = -1)
                }
                currentState.copy(allProxiesDetails = updatedDetails)
            }

            val jobs = currentNodes.map { node ->
                async(Dispatchers.IO) {
                    try {
                        val encodedName = URLEncoder.encode(node.name, "UTF-8").replace("+", "%20")
                        val request = Request.Builder()
                            .url("http://127.0.0.1:9090/proxies/${encodedName}/delay?timeout=3000&url=http://www.gstatic.com/generate_204")
                            .build()

                        client.newCall(request).execute().use { response ->
                            val latency = if (response.isSuccessful) {
                                val responseBody = response.body?.string()
                                if (responseBody != null) {
                                    JSONObject(responseBody).optInt("delay", 0)
                                } else 0
                            } else 0

                            withContext(Dispatchers.Main) {
                                _state.update { currentState ->
                                    val updatedDetails = currentState.allProxiesDetails.toMutableMap()
                                    val existing = updatedDetails[node.name]
                                    updatedDetails[node.name] = (existing ?: ProxyDetail(node.type, null)).copy(latency = latency)
                                    currentState.copy(allProxiesDetails = updatedDetails)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            _state.update { currentState ->
                                val updatedDetails = currentState.allProxiesDetails.toMutableMap()
                                val existing = updatedDetails[node.name]
                                updatedDetails[node.name] = (existing ?: ProxyDetail(node.type, null)).copy(latency = 0)
                                currentState.copy(allProxiesDetails = updatedDetails)
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    private fun selectProxy(node: ProxyNode) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentGroupName = _state.value.selectedGroupName
            if (currentGroupName.isBlank()) return@launch

            // Optimistic UI update
            _state.update { currentState ->
                val updatedGroups = currentState.groups.map { group ->
                    if (group.name == currentGroupName) {
                        group.copy(now = node.name)
                    } else group
                }
                currentState.copy(groups = updatedGroups)
            }

            try {
                val groupUrlEncoded = URLEncoder.encode(currentGroupName, "UTF-8").replace("+", "%20")
                val jsonBody = JSONObject().put("name", node.name).toString()
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("http://127.0.0.1:9090/proxies/$groupUrlEncoded")
                    .put(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        fetchProxies(silently = true)
                    } else {
                        _state.update { it.copy(error = "Failed to select proxy: ${response.code}") }
                        fetchProxies(silently = true)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Error selecting proxy: ${e.message}") }
                fetchProxies(silently = true)
            }
        }
    }

    private fun updateProxyMode(mode: ProxyMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val modeStr = when (mode) {
                ProxyMode.Direct -> "direct"
                ProxyMode.Global -> "global"
                ProxyMode.Rule -> "rule"
                ProxyMode.Unchanged -> return@launch
            }
            val app = getApplication<Application>()
            val currentOverrides = top.xing2387.mihotier.core.config.OverrideSettingsRepository.load(app)
            top.xing2387.mihotier.core.config.OverrideSettingsRepository.save(
                app,
                currentOverrides.copy(enableOverride = true, mode = modeStr)
            )
            if (top.xing2387.mihotier.core.config.EasyTierRepository.getBridgeRoutingInfo(app).enabled) {
                try {
                    val intent = android.content.Intent(app, top.xing2387.mihotier.core.vpn.UnifiedVpnService::class.java).apply {
                        action = top.xing2387.mihotier.core.vpn.UnifiedVpnService.ACTION_RESTART_VPN
                    }
                    app.startService(intent)
                    _state.update { it.copy(proxyMode = mode) }
                } catch (e: Exception) {
                    _state.update { it.copy(error = "Failed to switch mode: ${e.message}") }
                }
                return@launch
            }
            try {
                val jsonBody = JSONObject().put("mode", modeStr).toString()
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://127.0.0.1:9090/configs")
                    .patch(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        _state.update { it.copy(proxyMode = mode) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to switch mode: ${e.message}") }
            }
        }
    }
}
