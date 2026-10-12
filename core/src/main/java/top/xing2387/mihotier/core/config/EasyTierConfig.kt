package top.xing2387.mihotier.core.config

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import top.xing2387.mihotier.easytier.EasyTierNative
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class NetworkingMethod(val label: String) {
    PUBLIC_SERVER("公共服务器"),
    MANUAL("手动指定"),
    STANDALONE("独立节点")
}

data class EasyTierPortForward(
    val proto: String = "tcp",
    val bindAddr: String = "0.0.0.0:8080",
    val dstAddr: String = "10.144.144.2:80"
)

data class EasyTierNetworkProfile(
    val id: String = UUID.randomUUID().toString(),
    val instanceName: String = "easytier",
    val hostname: String = "",
    val dhcp: Boolean = true,
    val virtualIpv4: String = "10.144.144.1",
    val networkLength: Int = 24,
    val networkName: String = "easytier",
    val networkSecret: String = "",
    val networkingMethod: NetworkingMethod = NetworkingMethod.PUBLIC_SERVER,
    val publicServerUrl: String = "tcp://public.easytier.top:11010",
    val peerUrls: List<String> = listOf("tcp://public.easytier.top:11010"),
    val listenerUrls: List<String> = listOf(
        "tcp://0.0.0.0:11010",
        "udp://0.0.0.0:11010",
        "wg://0.0.0.0:11011"
    ),
    val proxyCidrs: List<String> = emptyList(),
    val socks5Enabled: Boolean = true,
    val socks5Port: Int = 10888,
    val defaultProtocol: String = "tcp",
    val devName: String = "easytier0",
    val mtu: Int = 1380,
    val enableEncryption: Boolean = true,
    val enableIpv6: Boolean = true,
    val latencyFirst: Boolean = false,
    val enableExitNode: Boolean = false,
    val noTun: Boolean = true,
    val useSmoltcp: Boolean = true,
    val disableP2p: Boolean = false,
    val disableUdpHolePunching: Boolean = false,
    val relayAllPeerRpc: Boolean = false,
    val enableKcpProxy: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val privateMode: Boolean = false,
    val portForwards: List<EasyTierPortForward> = emptyList(),
    val useCustomToml: Boolean = false,
    val customToml: String = ""
) {
    fun toTomlString(): String {
        if (useCustomToml && customToml.isNotBlank()) {
            return customToml
        }

        val sb = StringBuilder()
        val safeInstanceId = try {
            UUID.fromString(id).toString()
        } catch (_: Exception) {
            UUID.nameUUIDFromBytes(id.toByteArray()).toString()
        }

        sb.appendLine("instance_name = ${escapeTomlString(instanceName.ifBlank { "easytier" })}")
        sb.appendLine("instance_id = ${escapeTomlString(safeInstanceId)}")
        if (hostname.isNotBlank()) {
            sb.appendLine("hostname = ${escapeTomlString(hostname.trim())}")
        }
        sb.appendLine("dhcp = $dhcp")
        if (!dhcp && virtualIpv4.isNotBlank() && virtualIpv4.trim() != "0.0.0.0") {
            val cleanIp = virtualIpv4.trim().substringBefore("/")
            val prefix = networkLength.coerceIn(1, 32)
            sb.appendLine("ipv4 = ${escapeTomlString("$cleanIp/$prefix")}")
        }

        if (listenerUrls.isNotEmpty()) {
            val joined = listenerUrls.joinToString(", ") { escapeTomlString(it.trim()) }
            sb.appendLine("listeners = [$joined]")
        } else {
            sb.appendLine("listeners = []")
        }

        if (socks5Enabled && socks5Port in 1..65535) {
            sb.appendLine("socks5_proxy = ${escapeTomlString("socks5://0.0.0.0:$socks5Port")}")
        }

        sb.appendLine()
        sb.appendLine("[network_identity]")
        sb.appendLine("network_name = ${escapeTomlString(networkName.ifBlank { "easytier" })}")
        sb.appendLine("network_secret = ${escapeTomlString(networkSecret)}")

        val activePeers = when (networkingMethod) {
            NetworkingMethod.PUBLIC_SERVER -> {
                if (publicServerUrl.isNotBlank()) listOf(publicServerUrl.trim()) else emptyList()
            }
            NetworkingMethod.MANUAL -> peerUrls.map { it.trim() }.filter { it.isNotEmpty() }
            NetworkingMethod.STANDALONE -> emptyList()
        }

        activePeers.forEach { uri ->
            sb.appendLine()
            sb.appendLine("[[peer]]")
            sb.appendLine("uri = ${escapeTomlString(uri)}")
        }

        proxyCidrs.map { it.trim() }.filter { it.isNotEmpty() }.forEach { cidrStr ->
            sb.appendLine()
            sb.appendLine("[[proxy_network]]")
            sb.appendLine("cidr = ${escapeTomlString(cidrStr)}")
        }

        portForwards.forEach { pf ->
            if (pf.bindAddr.isNotBlank() && pf.dstAddr.isNotBlank()) {
                sb.appendLine()
                sb.appendLine("[[port_forward]]")
                sb.appendLine("proto = ${escapeTomlString(pf.proto.lowercase())}")
                sb.appendLine("bind_addr = ${escapeTomlString(pf.bindAddr.trim())}")
                sb.appendLine("dst_addr = ${escapeTomlString(pf.dstAddr.trim())}")
            }
        }

        sb.appendLine()
        sb.appendLine("[flags]")
        sb.appendLine("default_protocol = ${escapeTomlString(defaultProtocol)}")
        if (devName.isNotBlank()) {
            sb.appendLine("dev_name = ${escapeTomlString(devName.trim())}")
        }
        sb.appendLine("enable_encryption = $enableEncryption")
        sb.appendLine("enable_ipv6 = $enableIpv6")
        sb.appendLine("mtu = ${mtu.coerceIn(576, 9000)}")
        sb.appendLine("latency_first = $latencyFirst")
        sb.appendLine("enable_exit_node = $enableExitNode")
        sb.appendLine("no_tun = $noTun")
        sb.appendLine("use_smoltcp = $useSmoltcp")
        sb.appendLine("bind_device = false")
        sb.appendLine("disable_p2p = $disableP2p")
        sb.appendLine("relay_all_peer_rpc = $relayAllPeerRpc")
        sb.appendLine("disable_udp_hole_punching = $disableUdpHolePunching")
        sb.appendLine("enable_kcp_proxy = $enableKcpProxy")
        sb.appendLine("enable_quic_proxy = $enableQuicProxy")
        sb.appendLine("private_mode = $privateMode")

        return sb.toString()
    }

    private fun escapeTomlString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return "\"$escaped\""
    }
}

data class EasyTierPeerNodeInfo(
    val peerId: String,
    val hostname: String,
    val virtualIpv4: String,
    val tunnelProto: String,
    val natType: String,
    val latencyMs: Int,
    val lossRate: String,
    val rxBytes: String,
    val txBytes: String,
    val isRelay: Boolean = false
)

data class EasyTierRouteItem(
    val peerId: String,
    val hostname: String,
    val ipv4: String,
    val nextHop: String,
    val cost: Int,
    val proxyCidrs: List<String>
)

data class EasyTierRuntimeState(
    val isRunning: Boolean = false,
    val runningProfileId: String? = null,
    val runningProfileName: String = "",
    val allocatedIpv4: String = "",
    val networkName: String = "",
    val hostname: String = "",
    val socks5Portal: String = "",
    val natType: String = "Unknown",
    val uptimeSec: Long = 0L,
    val peers: List<EasyTierPeerNodeInfo> = emptyList(),
    val routes: List<EasyTierRouteItem> = emptyList(),
    val eventLogs: List<String> = emptyList(),
    val lastError: String = ""
)

data class EasyTierBridgeRoutingInfo(
    val enabled: Boolean,
    val socks5Port: Int,
    val cidrs: List<Pair<String, Int>>
) {
    val cidrStrings: List<String>
        get() = cidrs.map { "${it.first}/${it.second}" }
}

data class EasyTierStandaloneTunConfig(
    val enabled: Boolean = false,
    val localIp: String = "",
    val prefixLength: Int = 24,
    val mtu: Int = 1380,
    val routes: List<Pair<String, Int>> = emptyList()
)

object EasyTierRepository {
    private const val PREFS_NAME = "easytier_profiles_prefs"
    private const val KEY_PROFILES_JSON = "profiles_json"
    private const val KEY_SELECTED_ID = "selected_profile_id"
    private const val KEY_LAST_ALLOCATED_IPV4 = "last_allocated_ipv4"
    private const val KEY_LAST_PROXY_CIDRS = "last_proxy_cidrs"

    private val _profiles = MutableStateFlow<List<EasyTierNetworkProfile>>(emptyList())
    val profiles: StateFlow<List<EasyTierNetworkProfile>> = _profiles.asStateFlow()

    private val _selectedProfileId = MutableStateFlow("")
    val selectedProfileId: StateFlow<String> = _selectedProfileId.asStateFlow()

    private val _runtimeState = MutableStateFlow(EasyTierRuntimeState())
    val runtimeState: StateFlow<EasyTierRuntimeState> = _runtimeState.asStateFlow()

    private var appContext: Context? = null
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var initialized = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized && _profiles.value.isNotEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROFILES_JSON, null)
        val loaded = if (!raw.isNullOrBlank()) {
            parseProfilesJson(raw)
        } else {
            emptyList()
        }

        val finalProfiles = loaded.ifEmpty {
            listOf(
                EasyTierNetworkProfile(
                    id = UUID.randomUUID().toString(),
                    instanceName = "easytier",
                    hostname = "MihoTier-Node",
                    dhcp = false,
                    virtualIpv4 = "10.144.144.1",
                    networkLength = 24,
                    networkName = "easytier",
                    networkSecret = "",
                    networkingMethod = NetworkingMethod.MANUAL,
                    publicServerUrl = "tcp://public.easytier.top:11010",
                    peerUrls = listOf(
                        "tcp://public.easytier.top:11010"
                    )
                )
            )
        }

        _profiles.value = finalProfiles
        val savedSelected = prefs.getString(KEY_SELECTED_ID, null)
        _selectedProfileId.value = if (finalProfiles.any { it.id == savedSelected }) {
            savedSelected!!
        } else {
            finalProfiles.first().id
        }

        if (loaded.isEmpty()) {
            saveToPrefs(context)
        }
        initialized = true
        refreshRuntimeStatus()
        if (_runtimeState.value.isRunning) {
            startStatusPolling()
        }
    }

    private fun startStatusPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = repoScope.launch {
            while (isActive && _runtimeState.value.isRunning) {
                refreshRuntimeStatus()
                val alloc = _runtimeState.value.allocatedIpv4
                val fastPoll = alloc.isBlank() || alloc.contains("DHCP")
                delay(if (fastPoll) 500L else 2000L)
            }
        }
    }

    fun selectProfile(context: Context, id: String) {
        if (_profiles.value.any { it.id == id }) {
            _selectedProfileId.value = id
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_ID, id)
                .apply()
        }
    }

    fun createProfile(context: Context, name: String): EasyTierNetworkProfile {
        val cleanName = name.trim().ifEmpty { "easytier-${_profiles.value.size + 1}" }
        val newProfile = EasyTierNetworkProfile(
            id = UUID.randomUUID().toString(),
            instanceName = cleanName,
            hostname = "MihoTier-Android",
            dhcp = true,
            virtualIpv4 = "10.144.144.1",
            networkLength = 24,
            networkName = cleanName,
            networkSecret = "",
            networkingMethod = NetworkingMethod.PUBLIC_SERVER
        )
        _profiles.value = _profiles.value + newProfile
        _selectedProfileId.value = newProfile.id
        saveToPrefs(context)
        return newProfile
    }

    fun updateProfile(context: Context, updated: EasyTierNetworkProfile) {
        _profiles.value = _profiles.value.map { if (it.id == updated.id) updated else it }
        saveToPrefs(context)
    }

    fun deleteProfile(context: Context, id: String): Boolean {
        if (_profiles.value.size <= 1) return false
        if (_runtimeState.value.isRunning && _runtimeState.value.runningProfileId == id) {
            stopNetwork()
        }
        val remaining = _profiles.value.filterNot { it.id == id }
        _profiles.value = remaining
        if (_selectedProfileId.value == id) {
            _selectedProfileId.value = remaining.first().id
        }
        saveToPrefs(context)
        return true
    }

    fun validateToml(tomlStr: String): String {
        return try {
            EasyTierNative.nativeValidateConfig(tomlStr)
        } catch (e: Throwable) {
            "JNI 调用失败: ${e.message}"
        }
    }

    fun startNetwork(context: Context, profile: EasyTierNetworkProfile): Result<Unit> {
        appContext = context.applicationContext
        updateProfile(context, profile)
        val toml = profile.toTomlString()
        val validationError = validateToml(toml)
        if (validationError.isNotBlank()) {
            android.util.Log.e("EasyTierRepo", "Validation error: $validationError")
            _runtimeState.value = _runtimeState.value.copy(lastError = validationError)
            return Result.failure(IllegalArgumentException(validationError))
        }

        val code = try {
            EasyTierNative.nativeStartEasyTier(toml)
        } catch (e: Throwable) {
            android.util.Log.e("EasyTierRepo", "Exception starting EasyTier", e)
            return Result.failure(e)
        }

        if (code != 0) {
            val err = try {
                EasyTierNative.nativeGetLastError()
            } catch (_: Throwable) {
                "未知错误 (code=$code)"
            }
            android.util.Log.e("EasyTierRepo", "nativeStartEasyTier failed ($code): $err")
            _runtimeState.value = _runtimeState.value.copy(lastError = err)
            return Result.failure(IllegalStateException(err))
        }

        _runtimeState.value = _runtimeState.value.copy(
            isRunning = true,
            runningProfileId = profile.id,
            runningProfileName = profile.instanceName
        )
        refreshRuntimeStatus(profile)
        startStatusPolling()
        try {
            val intent = android.content.Intent(context, top.xing2387.mihotier.core.vpn.UnifiedVpnService::class.java).apply {
                action = top.xing2387.mihotier.core.vpn.UnifiedVpnService.ACTION_SYNC_EASYTIER_TUN
            }
            context.startService(intent)
        } catch (_: Throwable) {
        }
        return Result.success(Unit)
    }

    fun stopNetwork(): Result<Unit> {
        return try {
            pollingJob?.cancel()
            pollingJob = null
            EasyTierNative.nativeStopEasyTier()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val prevLogs = _runtimeState.value.eventLogs
            _runtimeState.value = EasyTierRuntimeState(
                isRunning = false,
                runningProfileId = null,
                eventLogs = (prevLogs + "[$timeStr] [INFO] 已停止 EasyTier 组网实例").takeLast(50)
            )
            appContext?.let { ctx ->
                try {
                    val intent = android.content.Intent(ctx, top.xing2387.mihotier.core.vpn.UnifiedVpnService::class.java).apply {
                        action = top.xing2387.mihotier.core.vpn.UnifiedVpnService.ACTION_SYNC_EASYTIER_TUN
                    }
                    ctx.startService(intent)
                } catch (_: Throwable) {
                }
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun refreshRuntimeStatus(activeProfile: EasyTierNetworkProfile? = null) {
        try {
            val statusJsonStr = EasyTierNative.nativeGetNodeStatus()
            val json = JSONObject(statusJsonStr)
            val running = json.optBoolean("running", false)
            if (!running) {
                _runtimeState.value = _runtimeState.value.copy(
                    isRunning = false,
                    runningProfileId = null
                )
                return
            }

            val profile = activeProfile
                ?: _profiles.value.find { it.id == _runtimeState.value.runningProfileId }
                ?: _profiles.value.find { it.id == _selectedProfileId.value }

            val rawIpv4 = json.optString("ipv4", "")
            val isDhcp = json.optBoolean("dhcp", profile?.dhcp == true)
            val ipv4 = if (rawIpv4.isNotBlank()) {
                rawIpv4
            } else if (isDhcp) {
                "DHCP 协商中..."
            } else {
                "${profile?.virtualIpv4 ?: "0.0.0.0"}/${profile?.networkLength ?: 24}"
            }

            val networkName = json.optString("network_name", profile?.networkName ?: "easytier")
            val hostname = json.optString("hostname", "").ifBlank {
                profile?.hostname?.ifBlank { "MihoTier-Android" } ?: "MihoTier-Android"
            }
            val socks5Portal = json.optString("socks5_portal", "").ifBlank {
                if (profile?.socks5Enabled == true) "socks5://127.0.0.1:${profile.socks5Port}" else "未启用"
            }
            val natType = json.optString("nat_type", "Unknown")
            val uptimeSec = json.optLong("uptime_sec", 0L)
            val errorMsg = json.optString("error_msg", "")

            val peersArray = json.optJSONArray("peers") ?: JSONArray()
            val peerNodes = mutableListOf<EasyTierPeerNodeInfo>()

            for (i in 0 until peersArray.length()) {
                val pObj = peersArray.optJSONObject(i) ?: continue
                peerNodes.add(
                    EasyTierPeerNodeInfo(
                        peerId = pObj.optString("peer_id", ""),
                        hostname = pObj.optString("hostname", "unknown"),
                        virtualIpv4 = pObj.optString("ipv4", ""),
                        tunnelProto = pObj.optString("tunnel_proto", "UDP"),
                        natType = pObj.optString("nat_type", "Unknown"),
                        latencyMs = pObj.optInt("latency_ms", 0),
                        lossRate = pObj.optString("loss_rate", "0.0%"),
                        rxBytes = pObj.optString("rx_bytes", "0 B"),
                        txBytes = pObj.optString("tx_bytes", "0 B"),
                        isRelay = pObj.optBoolean("is_relay", false)
                    )
                )
            }

            val routesArray = json.optJSONArray("routes") ?: JSONArray()
            val routeItems = mutableListOf<EasyTierRouteItem>()
            for (i in 0 until routesArray.length()) {
                val rObj = routesArray.optJSONObject(i) ?: continue
                val cidrs = mutableListOf<String>()
                rObj.optJSONArray("proxy_cidrs")?.let { ca ->
                    for (j in 0 until ca.length()) {
                        val c = ca.optString(j)
                        if (c.isNotBlank()) cidrs.add(c)
                    }
                }
                routeItems.add(
                    EasyTierRouteItem(
                        peerId = rObj.optString("peer_id", ""),
                        hostname = rObj.optString("hostname", ""),
                        ipv4 = rObj.optString("ipv4", ""),
                        nextHop = rObj.optString("next_hop", ""),
                        cost = rObj.optInt("cost", 1),
                        proxyCidrs = cidrs
                    )
                )
            }

            // Persist discovered virtual IPv4 and proxy CIDRs for immediate VPN routing on startup
            appContext?.let { ctx ->
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                var changed = false
                if (normalizeToNetworkCidr(rawIpv4) != null) {
                    if (prefs.getString(KEY_LAST_ALLOCATED_IPV4, null) != rawIpv4) {
                        editor.putString(KEY_LAST_ALLOCATED_IPV4, rawIpv4)
                        changed = true
                    }
                }
                val discoveredProxyCidrs = routeItems.flatMap { it.proxyCidrs }.distinct()
                if (discoveredProxyCidrs.isNotEmpty()) {
                    val joined = discoveredProxyCidrs.joinToString(",")
                    if (prefs.getString(KEY_LAST_PROXY_CIDRS, null) != joined) {
                        editor.putString(KEY_LAST_PROXY_CIDRS, joined)
                        changed = true
                    }
                }
                if (changed) editor.apply()
            }

            val eventsArray = json.optJSONArray("events") ?: JSONArray()
            val logs = mutableListOf<String>()
            for (i in 0 until eventsArray.length()) {
                val rawEvent = eventsArray.optString(i)
                if (rawEvent.isNotBlank()) {
                    logs.add(formatEasyTierEvent(rawEvent))
                }
            }
            if (errorMsg.isNotBlank()) {
                logs.add("[ERROR] $errorMsg")
            }

            android.util.Log.d(
                "EasyTierRepo",
                "Status: ipv4=$ipv4, peers=${peerNodes.size}, routes=${routeItems.size}, events=${logs.size}, err=$errorMsg"
            )

            _runtimeState.value = EasyTierRuntimeState(
                isRunning = true,
                runningProfileId = profile?.id ?: _runtimeState.value.runningProfileId,
                runningProfileName = profile?.instanceName ?: _runtimeState.value.runningProfileName,
                allocatedIpv4 = ipv4,
                networkName = networkName,
                hostname = hostname,
                socks5Portal = socks5Portal,
                natType = natType,
                uptimeSec = uptimeSec,
                peers = peerNodes,
                routes = routeItems,
                eventLogs = logs.takeLast(40),
                lastError = errorMsg
            )
        } catch (e: Throwable) {
            android.util.Log.e("EasyTierRepo", "refreshRuntimeStatus error", e)
        }
    }

    /**
     * Compute active EasyTier SOCKS5 port and normalized virtual LAN / subnet proxy CIDRs
     * so Mihomo and UnifiedVpnService can automatically route EasyTier traffic.
     */
    fun getBridgeRoutingInfo(context: Context): EasyTierBridgeRoutingInfo {
        init(context)
        val state = _runtimeState.value
        val profile = _profiles.value.find { it.id == state.runningProfileId }
            ?: _profiles.value.find { it.id == _selectedProfileId.value }
            ?: _profiles.value.firstOrNull()

        val portalPort = state.socks5Portal
            .substringAfterLast(":", "")
            .trim()
            .toIntOrNull()
        val socks5Port = portalPort ?: (profile?.socks5Port ?: 10888)
        val enabled = state.isRunning && (profile?.socks5Enabled != false) && (socks5Port in 1..65535)

        val cidrs = LinkedHashSet<Pair<String, Int>>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Live allocated IPv4 (DHCP or static)
        normalizeToNetworkCidr(state.allocatedIpv4, defaultPrefix = profile?.networkLength ?: 24)?.let {
            cidrs.add(it)
        }

        // 2. Live peer virtual IPs and route IPs / proxy CIDRs
        state.peers.forEach { peer ->
            normalizeToNetworkCidr(peer.virtualIpv4, defaultPrefix = 24)?.let { cidrs.add(it) }
        }
        state.routes.forEach { route ->
            normalizeToNetworkCidr(route.ipv4, defaultPrefix = 24)?.let { cidrs.add(it) }
            route.proxyCidrs.forEach { proxyCidr ->
                normalizeToNetworkCidr(proxyCidr, defaultPrefix = 32)?.let { cidrs.add(it) }
            }
        }

        // 3. Profile static IPv4 or last persisted DHCP IPv4 (so VPN start before DHCP completes still routes properly)
        if (profile != null && !profile.dhcp) {
            normalizeToNetworkCidr("${profile.virtualIpv4}/${profile.networkLength}")?.let {
                cidrs.add(it)
            }
        } else {
            val savedDhcpIp = prefs.getString(KEY_LAST_ALLOCATED_IPV4, null)
            if (!savedDhcpIp.isNullOrBlank()) {
                normalizeToNetworkCidr(savedDhcpIp)?.let { cidrs.add(it) }
            }
        }

        // 4. Profile proxyCidrs and last persisted peer proxyCidrs
        profile?.proxyCidrs?.forEach { proxyCidr ->
            normalizeToNetworkCidr(proxyCidr, defaultPrefix = 24)?.let { cidrs.add(it) }
        }
        prefs.getString(KEY_LAST_PROXY_CIDRS, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.forEach { proxyCidr ->
                normalizeToNetworkCidr(proxyCidr, defaultPrefix = 32)?.let { cidrs.add(it) }
            }

        return EasyTierBridgeRoutingInfo(
            enabled = enabled && cidrs.isNotEmpty(),
            socks5Port = socks5Port,
            cidrs = cidrs.toList()
        )
    }

    /**
     * Computes the standalone Android TUN configuration for EasyTier when Mihomo is NOT running.
     */
    fun getStandaloneTunConfig(context: Context): EasyTierStandaloneTunConfig {
        init(context)
        val state = _runtimeState.value
        if (!state.isRunning) return EasyTierStandaloneTunConfig(enabled = false)

        val profile = _profiles.value.find { it.id == state.runningProfileId }
            ?: _profiles.value.find { it.id == _selectedProfileId.value }
            ?: _profiles.value.firstOrNull()

        val defaultPrefix = profile?.networkLength ?: 24
        val hostAndPrefix = parseHostIpAndPrefix(state.allocatedIpv4, defaultPrefix = defaultPrefix)
            ?: if (profile != null && !profile.dhcp) {
                parseHostIpAndPrefix("${profile.virtualIpv4}/${profile.networkLength}", defaultPrefix = defaultPrefix)
            } else {
                null
            }
            ?: return EasyTierStandaloneTunConfig(enabled = false)

        val (localIp, prefixLen) = hostAndPrefix
        val routes = LinkedHashSet<Pair<String, Int>>()
        normalizeToNetworkCidr("$localIp/$prefixLen")?.let { routes.add(it) }
        state.routes.forEach { route ->
            normalizeToNetworkCidr(route.ipv4, defaultPrefix = prefixLen)?.let { routes.add(it) }
            route.proxyCidrs.forEach { proxyCidr ->
                normalizeToNetworkCidr(proxyCidr, defaultPrefix = 32)?.let { routes.add(it) }
            }
        }
        profile?.proxyCidrs?.forEach { proxyCidr ->
            normalizeToNetworkCidr(proxyCidr, defaultPrefix = 24)?.let { routes.add(it) }
        }

        val mtu = (profile?.mtu ?: 1380).coerceIn(1280, 9000)
        return EasyTierStandaloneTunConfig(
            enabled = true,
            localIp = localIp,
            prefixLength = prefixLen,
            mtu = mtu,
            routes = routes.toList()
        )
    }

    /**
     * Parses a host IPv4 address and prefix (e.g. "10.126.126.2/24" -> "10.126.126.2" to 24)
     * without masking host bits.
     */
    fun parseHostIpAndPrefix(raw: String, defaultPrefix: Int = 24): Pair<String, Int>? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty() || !cleaned[0].isDigit()) return null
        val parts = cleaned.split("/")
        val ipStr = parts[0].trim()
        val prefix = if (parts.size > 1) {
            parts[1].trim().toIntOrNull() ?: return null
        } else {
            defaultPrefix
        }.coerceIn(1, 32)

        val octets = ipStr.split(".")
        if (octets.size != 4) return null
        val b0 = octets[0].toIntOrNull() ?: return null
        val b1 = octets[1].toIntOrNull() ?: return null
        val b2 = octets[2].toIntOrNull() ?: return null
        val b3 = octets[3].toIntOrNull() ?: return null
        if (b0 !in 0..255 || b1 !in 0..255 || b2 !in 0..255 || b3 !in 0..255) return null
        if (b0 == 0 || b0 == 127) return null

        return "$b0.$b1.$b2.$b3" to prefix
    }

    /**
     * Parses an IPv4 address or CIDR string (e.g. "192.168.100.3/24" or "192.168.100.16")
     * and masks the host bits to return the canonical network address and prefix length
     * (e.g. "192.168.100.0" to 24).
     */
    fun normalizeToNetworkCidr(raw: String, defaultPrefix: Int = 24): Pair<String, Int>? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty() || !cleaned[0].isDigit()) return null
        val parts = cleaned.split("/")
        val ipStr = parts[0].trim()
        val prefix = if (parts.size > 1) {
            parts[1].trim().toIntOrNull() ?: return null
        } else {
            defaultPrefix
        }.coerceIn(1, 32)

        val octets = ipStr.split(".")
        if (octets.size != 4) return null
        val b0 = octets[0].toIntOrNull() ?: return null
        val b1 = octets[1].toIntOrNull() ?: return null
        val b2 = octets[2].toIntOrNull() ?: return null
        val b3 = octets[3].toIntOrNull() ?: return null
        if (b0 !in 0..255 || b1 !in 0..255 || b2 !in 0..255 || b3 !in 0..255) return null
        if (b0 == 0 || b0 == 127) return null

        val ipInt = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        val mask = if (prefix == 32) -1 else (-1 shl (32 - prefix))
        val netInt = ipInt and mask

        val n0 = (netInt ushr 24) and 0xFF
        val n1 = (netInt ushr 16) and 0xFF
        val n2 = (netInt ushr 8) and 0xFF
        val n3 = netInt and 0xFF
        return "$n0.$n1.$n2.$n3" to prefix
    }

    private fun formatEasyTierEvent(rawJson: String): String {
        return try {
            val obj = JSONObject(rawJson)
            val timeRaw = obj.optString("time", "")
            val timeShort = if (timeRaw.contains("T")) {
                timeRaw.substringAfter("T").take(8)
            } else {
                timeRaw.take(19)
            }
            val eventObj = obj.optJSONObject("event")
            if (eventObj != null) {
                val eventType = eventObj.keys().asSequence().firstOrNull() ?: "Event"
                val detail = eventObj.opt(eventType)?.toString() ?: ""
                "[$timeShort] $eventType: $detail"
            } else {
                rawJson
            }
        } catch (_: Exception) {
            rawJson
        }
    }

    private fun saveToPrefs(context: Context) {
        val arr = JSONArray()
        _profiles.value.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("instanceName", p.instanceName)
                put("hostname", p.hostname)
                put("dhcp", p.dhcp)
                put("virtualIpv4", p.virtualIpv4)
                put("networkLength", p.networkLength)
                put("networkName", p.networkName)
                put("networkSecret", p.networkSecret)
                put("networkingMethod", p.networkingMethod.name)
                put("publicServerUrl", p.publicServerUrl)
                put("peerUrls", JSONArray(p.peerUrls))
                put("listenerUrls", JSONArray(p.listenerUrls))
                put("proxyCidrs", JSONArray(p.proxyCidrs))
                put("socks5Enabled", p.socks5Enabled)
                put("socks5Port", p.socks5Port)
                put("defaultProtocol", p.defaultProtocol)
                put("devName", p.devName)
                put("mtu", p.mtu)
                put("enableEncryption", p.enableEncryption)
                put("enableIpv6", p.enableIpv6)
                put("latencyFirst", p.latencyFirst)
                put("enableExitNode", p.enableExitNode)
                put("noTun", p.noTun)
                put("useSmoltcp", p.useSmoltcp)
                put("disableP2p", p.disableP2p)
                put("disableUdpHolePunching", p.disableUdpHolePunching)
                put("relayAllPeerRpc", p.relayAllPeerRpc)
                put("enableKcpProxy", p.enableKcpProxy)
                put("enableQuicProxy", p.enableQuicProxy)
                put("privateMode", p.privateMode)
                val pfArr = JSONArray()
                p.portForwards.forEach { pf ->
                    pfArr.put(
                        JSONObject().apply {
                            put("proto", pf.proto)
                            put("bindAddr", pf.bindAddr)
                            put("dstAddr", pf.dstAddr)
                        }
                    )
                }
                put("portForwards", pfArr)
                put("useCustomToml", p.useCustomToml)
                put("customToml", p.customToml)
            }
            arr.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILES_JSON, arr.toString())
            .putString(KEY_SELECTED_ID, _selectedProfileId.value)
            .apply()
    }

    private fun parseProfilesJson(raw: String): List<EasyTierNetworkProfile> {
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<EasyTierNetworkProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val peerUrls = mutableListOf<String>()
                obj.optJSONArray("peerUrls")?.let { ja ->
                    for (j in 0 until ja.length()) {
                        val s = ja.optString(j)
                        if (s.isNotBlank()) peerUrls.add(s)
                    }
                }
                val listenerUrls = mutableListOf<String>()
                obj.optJSONArray("listenerUrls")?.let { ja ->
                    for (j in 0 until ja.length()) {
                        val s = ja.optString(j)
                        if (s.isNotBlank()) listenerUrls.add(s)
                    }
                }
                val proxyCidrs = mutableListOf<String>()
                obj.optJSONArray("proxyCidrs")?.let { ja ->
                    for (j in 0 until ja.length()) {
                        val s = ja.optString(j)
                        if (s.isNotBlank()) proxyCidrs.add(s)
                    }
                }
                val portForwards = mutableListOf<EasyTierPortForward>()
                obj.optJSONArray("portForwards")?.let { ja ->
                    for (j in 0 until ja.length()) {
                        val pfObj = ja.optJSONObject(j) ?: continue
                        portForwards.add(
                            EasyTierPortForward(
                                proto = pfObj.optString("proto", "tcp"),
                                bindAddr = pfObj.optString("bindAddr", ""),
                                dstAddr = pfObj.optString("dstAddr", "")
                            )
                        )
                    }
                }
                val methodStr = obj.optString("networkingMethod", NetworkingMethod.MANUAL.name)
                val method = try {
                    NetworkingMethod.valueOf(methodStr)
                } catch (_: Exception) {
                    NetworkingMethod.MANUAL
                }

                result.add(
                    EasyTierNetworkProfile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        instanceName = obj.optString("instanceName", "easytier"),
                        hostname = obj.optString("hostname", ""),
                        dhcp = obj.optBoolean("dhcp", false),
                        virtualIpv4 = obj.optString("virtualIpv4", "10.144.144.1"),
                        networkLength = obj.optInt("networkLength", 24),
                        networkName = obj.optString("networkName", "easytier"),
                        networkSecret = obj.optString("networkSecret", ""),
                        networkingMethod = method,
                        publicServerUrl = obj.optString("publicServerUrl", "tcp://public.easytier.top:11010"),
                        peerUrls = peerUrls.ifEmpty { listOf("tcp://public.easytier.top:11010") },
                        listenerUrls = listenerUrls.ifEmpty {
                            listOf("tcp://0.0.0.0:11010", "udp://0.0.0.0:11010", "wg://0.0.0.0:11011")
                        },
                        proxyCidrs = proxyCidrs,
                        socks5Enabled = obj.optBoolean("socks5Enabled", true),
                        socks5Port = obj.optInt("socks5Port", 10888),
                        defaultProtocol = obj.optString("defaultProtocol", "tcp"),
                        devName = obj.optString("devName", "easytier0"),
                        mtu = obj.optInt("mtu", 1380),
                        enableEncryption = obj.optBoolean("enableEncryption", true),
                        enableIpv6 = obj.optBoolean("enableIpv6", true),
                        latencyFirst = obj.optBoolean("latencyFirst", false),
                        enableExitNode = obj.optBoolean("enableExitNode", false),
                        noTun = obj.optBoolean("noTun", true),
                        useSmoltcp = obj.optBoolean("useSmoltcp", true),
                        disableP2p = obj.optBoolean("disableP2p", false),
                        disableUdpHolePunching = obj.optBoolean("disableUdpHolePunching", false),
                        relayAllPeerRpc = obj.optBoolean("relayAllPeerRpc", false),
                        enableKcpProxy = obj.optBoolean("enableKcpProxy", false),
                        enableQuicProxy = obj.optBoolean("enableQuicProxy", false),
                        privateMode = obj.optBoolean("privateMode", false),
                        portForwards = portForwards,
                        useCustomToml = obj.optBoolean("useCustomToml", false),
                        customToml = obj.optString("customToml", "")
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
