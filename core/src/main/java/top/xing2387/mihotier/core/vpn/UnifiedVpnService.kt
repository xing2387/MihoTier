package top.xing2387.mihotier.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import mobile.Mobile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import top.xing2387.mihotier.core.R
import top.xing2387.mihotier.core.config.AppSettingsRepository
import top.xing2387.mihotier.core.config.BandwidthFormatter
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.EasyTierRepository
import top.xing2387.mihotier.core.config.NetworkSettingsRepository
import top.xing2387.mihotier.core.config.ProfileRepository
import top.xing2387.mihotier.easytier.EasyTierNative
import top.xing2387.mihotier.easytier.SocketProtectCallback
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UnifiedVpnService : VpnService(), SocketProtectCallback {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var easyTierVpnInterface: ParcelFileDescriptor? = null
    private val tunMutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isEasyTierTunActive = MutableStateFlow(false)
    val isEasyTierTunActive: StateFlow<Boolean> = _isEasyTierTunActive.asStateFlow()

    data class TrafficStats(
        val upSpeed: Long = 0L,
        val downSpeed: Long = 0L,
        val upTotal: Long = 0L,
        val downTotal: Long = 0L,
        val easyTierUpSpeed: Long = 0L,
        val easyTierDownSpeed: Long = 0L,
        val easyTierUpTotal: Long = 0L,
        val easyTierDownTotal: Long = 0L,
        val tick: Long = 0L
    )

    private val _trafficStats = MutableStateFlow(TrafficStats())
    val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var currentModeName: String = "规则"

    @Volatile
    private var isForegroundActive: Boolean = false

    @Volatile
    private var appliedEasyTierSignature: String = ""

    @Volatile
    private var appliedEasyTierStandaloneSignature: String = ""

    @Volatile
    private var isRestartingVpn: Boolean = false

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trafficPollingJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): UnifiedVpnService = this@UnifiedVpnService

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == LAST_CALL_TRANSACTION) {
                Log.i(TAG, "LAST_CALL_TRANSACTION received on LocalBinder")
                onRevoke()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "UnifiedVpnService created")
        EasyTierRepository.init(this)
        serviceScope.launch {
            EasyTierRepository.runtimeState.collect { etState ->
                if (_isRunning.value && !isRestartingVpn) {
                    val info = EasyTierRepository.getBridgeRoutingInfo(this@UnifiedVpnService)
                    val newSig = "${info.enabled}:${info.socks5Port}:${info.cidrStrings.sorted().joinToString(",")}"
                    if (newSig != appliedEasyTierSignature) {
                        Log.i(TAG, "EasyTier routing changed ($appliedEasyTierSignature -> $newSig), reloading VPN")
                        restartVpn()
                    }
                } else if (!_isRunning.value && !isRestartingVpn) {
                    syncEasyTierStandaloneTun()
                }
                if (_isRunning.value || etState.isRunning) {
                    ensureTrafficPollingStarted()
                } else if (!isRestartingVpn) {
                    stopTrafficPollingAndNotification()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == SERVICE_INTERFACE) {
            Log.d(TAG, "System binding with SERVICE_INTERFACE, returning super.onBind()")
            return super.onBind(intent)
        }
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> startVpn()
            ACTION_STOP_VPN -> stopVpn()
            ACTION_RESTART_VPN -> restartVpn()
            ACTION_SYNC_EASYTIER_TUN -> {
                serviceScope.launch {
                    if (!_isRunning.value && !isRestartingVpn) {
                        syncEasyTierStandaloneTun()
                    }
                    if (_isRunning.value || EasyTierRepository.runtimeState.value.isRunning) {
                        ensureTrafficPollingStarted()
                    } else if (!isRestartingVpn) {
                        stopTrafficPollingAndNotification()
                    }
                }
            }
        }
        return START_STICKY
    }

    fun toggleVpn() {
        if (_isRunning.value) {
            stopVpn()
        } else {
            startVpn()
        }
    }

    fun restartVpn() {
        if (!_isRunning.value || isRestartingVpn) return
        isRestartingVpn = true
        serviceScope.launch {
            try {
                stopVpnInternal(shouldStopSelf = false)
                startVpnInternal()
            } finally {
                isRestartingVpn = false
            }
        }
    }

    private fun startVpn() {
        if (_isRunning.value) return
        serviceScope.launch {
            startVpnInternal()
        }
    }

    private suspend fun startVpnInternal() {
        if (_isRunning.value) return
        _isRunning.value = true

        EasyTierRepository.init(this@UnifiedVpnService)
        val initialBridge = EasyTierRepository.getBridgeRoutingInfo(this@UnifiedVpnService)
        appliedEasyTierSignature = "${initialBridge.enabled}:${initialBridge.socks5Port}:${initialBridge.cidrStrings.sorted().joinToString(",")}"

        try {
            // If EasyTier was directly holding the Android TUN fd, detach and close it first
            stopEasyTierStandaloneTun()

            // 3. Start Mihomo/Sing-box Core
            ProfileRepository.init(this@UnifiedVpnService)
            val easyTierBridge = EasyTierRepository.getBridgeRoutingInfo(this@UnifiedVpnService)
            appliedEasyTierSignature = "${easyTierBridge.enabled}:${easyTierBridge.socks5Port}:${easyTierBridge.cidrStrings.sorted().joinToString(",")}"

            val configStr = ConfigManager.getConfig(this@UnifiedVpnService)
            val configuredMode = ConfigManager.getConfiguredClashMode(this@UnifiedVpnService)
                currentModeName = when (configuredMode) {
                    "global" -> "全局"
                    "direct" -> "直连"
                    "rule" -> "规则"
                    "script" -> "脚本"
                    else -> "规则"
                }
                val netSettings = NetworkSettingsRepository.load(this@UnifiedVpnService)

                // Establish VpnService Interface
                val builder = Builder()
                builder.setSession("Mihotier VPN")
                    .setMtu(9000)
                    .addAddress("172.19.0.1", 30)

                // IPv6
                if (netSettings.allowIpv6) {
                    try {
                        builder.addAddress("fdfe:dcba:9876::1", 126)
                        if (netSettings.routeSystemTraffic) {
                            if (netSettings.bypassPrivateNetwork) {
                                builder.addRoute("2000::", 3)
                            } else {
                                builder.addRoute("::", 0)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to configure IPv6", e)
                    }
                }

                // DNS
                if (netSettings.dnsHijacking) {
                    builder.addDnsServer("198.18.0.2")
                }

                // Routes (Route System Traffic & Bypass Private Networks + EasyTier Virtual Subnets)
                if (netSettings.routeSystemTraffic) {
                    addRoutes(builder, netSettings.bypassPrivateNetwork, easyTierBridge.cidrs)
                } else {
                    builder.addRoute("198.18.0.0", 15)
                    for ((addr, prefix) in easyTierBridge.cidrs) {
                        try {
                            builder.addRoute(addr, prefix)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to add EasyTier route $addr/$prefix", e)
                        }
                    }
                }
                if (easyTierBridge.cidrs.isNotEmpty()) {
                    Log.i(TAG, "EasyTier VPN routes active: ${easyTierBridge.cidrStrings} -> SOCKS5 127.0.0.1:${easyTierBridge.socks5Port}")
                }

                // Allow apps to bypass VPN
                if (netSettings.allowBypass) {
                    builder.allowBypass()
                }

                // System HTTP Proxy (Android Q+)
                if (netSettings.systemProxy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val port = Regex("""(?m)^(?:mixed-port|port):\s*(\d+)""").find(configStr)?.groupValues?.get(1)?.toIntOrNull() ?: 7890
                        builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", port))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set HTTP proxy info", e)
                    }
                }

                // Access Control (Per-app proxy)
                // Note: Android VpnService.Builder does NOT allow mixing addDisallowedApplication()
                // and addAllowedApplication() on the same Builder instance.
                when (netSettings.accessControlMode) {
                    "Disallow selected apps" -> {
                        builder.addDisallowedApplication(packageName)
                        var count = 0
                        for (pkg in netSettings.accessControlPackages) {
                            if (pkg != packageName) {
                                try {
                                    builder.addDisallowedApplication(pkg)
                                    count++
                                } catch (e: Exception) {
                                    Log.w(TAG, "Cannot disallow $pkg", e)
                                }
                            }
                        }
                        Log.i(TAG, "Access control: Disallow mode active ($count apps bypassed)")
                    }
                    "Allow selected apps" -> {
                        // In Allow mode, any app NOT added via addAllowedApplication (including self packageName)
                        // automatically bypasses the VPN.
                        var allowedCount = 0
                        for (pkg in netSettings.accessControlPackages) {
                            if (pkg != packageName) {
                                try {
                                    builder.addAllowedApplication(pkg)
                                    allowedCount++
                                } catch (e: Exception) {
                                    Log.w(TAG, "Cannot allow $pkg", e)
                                }
                            }
                        }
                        if (allowedCount == 0) {
                            // Empty whitelist: disallow all installed apps so no app is routed through VPN
                            try {
                                for (pkgInfo in packageManager.getInstalledPackages(0)) {
                                    try {
                                        builder.addDisallowedApplication(pkgInfo.packageName)
                                    } catch (_: Exception) {
                                    }
                                }
                            } catch (e: Exception) {
                                builder.addDisallowedApplication(packageName)
                            }
                        }
                        Log.i(TAG, "Access control: Allow (whitelist) mode active ($allowedCount apps allowed)")
                    }
                    else -> {
                        // "Allow all apps"
                        builder.addDisallowedApplication(packageName)
                        Log.i(TAG, "Access control: Allow all apps (self excluded)")
                    }
                }

                vpnInterface = builder.establish()
                // detachFd() transfers fd ownership to Go/sing-tun.
                // After this, Java's ParcelFileDescriptor no longer owns the fd,
                // so Go can freely close it without triggering fdsan.
                val fd = vpnInterface?.detachFd() ?: -1
                Log.i(TAG, "VPN Tunnel established with fd=$fd")

                // Copy Geo data from assets to homeDir if they don't exist
                copyAssetsToHomeDir()

                // Pass installed package list from Android SDK to Go layer
                // This enables per-app routing (PROCESS-NAME rules) without
                // needing root access to /data/system/packages.xml
                injectPackageList()

                // Start Mihomo via JNI
                Log.i(TAG, "Preparing to start Mihomo via JNI with config length: ${configStr.length}")
                withContext(Dispatchers.IO) {
                    try {
                        Mobile.startWithFd(filesDir.absolutePath, configStr, fd.toLong())
                        Log.i(TAG, "Mihomo started successfully!")
                    } catch (e: Exception) {
                        Log.e(TAG, "Mihomo startWithFd threw exception!", e)
                        throw e
                    }
                }

                ensureGlobalDefaultNode()

                // 4. Start Foreground Notification (if enabled in settings)
                if (AppSettingsRepository.isShowSpeedNotification(this@UnifiedVpnService)) {
                    updateNotification(_trafficStats.value)
                } else {
                    cancelNotification()
                }

                // 5. Start Traffic Polling
                ensureTrafficPollingStarted()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun addRoutes(
        builder: Builder,
        bypassPrivateNetwork: Boolean,
        easyTierCidrs: List<Pair<String, Int>> = emptyList()
    ) {
        if (!bypassPrivateNetwork) {
            builder.addRoute("0.0.0.0", 0)
            return
        }

        val bypassRoutes = listOf(
            "1.0.0.0" to 8,
            "2.0.0.0" to 7,
            "4.0.0.0" to 6,
            "8.0.0.0" to 7,
            "11.0.0.0" to 8,
            "12.0.0.0" to 6,
            "16.0.0.0" to 4,
            "32.0.0.0" to 3,
            "64.0.0.0" to 2,
            "128.0.0.0" to 3,
            "160.0.0.0" to 5,
            "168.0.0.0" to 6,
            "172.0.0.0" to 12,
            "172.32.0.0" to 11,
            "172.64.0.0" to 10,
            "172.128.0.0" to 9,
            "173.0.0.0" to 8,
            "174.0.0.0" to 7,
            "176.0.0.0" to 4,
            "192.1.0.0" to 16,
            "192.2.0.0" to 15,
            "192.4.0.0" to 14,
            "192.8.0.0" to 13,
            "192.16.0.0" to 12,
            "192.32.0.0" to 11,
            "192.64.0.0" to 10,
            "192.128.0.0" to 11,
            "192.160.0.0" to 13,
            "192.169.0.0" to 16,
            "192.170.0.0" to 15,
            "192.172.0.0" to 14,
            "192.176.0.0" to 12,
            "192.192.0.0" to 10,
            "193.0.0.0" to 8,
            "194.0.0.0" to 7,
            "196.0.0.0" to 6,
            "200.0.0.0" to 5,
            "208.0.0.0" to 4,
            "198.18.0.0" to 15
        )
        for ((addr, prefix) in bypassRoutes) {
            try {
                builder.addRoute(addr, prefix)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add route $addr/$prefix", e)
            }
        }
        // Explicitly route EasyTier virtual LAN and subnet proxy CIDRs into the VPN TUN
        // even when private RFC1918 networks are bypassed.
        for ((addr, prefix) in easyTierCidrs) {
            try {
                builder.addRoute(addr, prefix)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add EasyTier route $addr/$prefix", e)
            }
        }
    }

    private fun copyAssetsToHomeDir() {
        val assetsToCopy = listOf("geoip.metadb", "geosite.dat", "country.mmdb")
        for (assetName in assetsToCopy) {
            val outFile = File(filesDir, assetName)
            if (!outFile.exists()) {
                try {
                    assets.open(assetName).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Copied asset $assetName to filesDir")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy asset $assetName", e)
                }
            }
        }
    }

    private fun injectPackageList() {
        try {
            val pm = applicationContext.packageManager
            val packages = pm.getInstalledPackages(0)
            val jsonArray = JSONArray()
            for (pkg in packages) {
                val obj = JSONObject()
                obj.put("packageName", pkg.packageName)
                obj.put("uid", pkg.applicationInfo?.uid ?: 0)
                jsonArray.put(obj)
            }
            val jsonStr = jsonArray.toString()
            Mobile.setPackageList(jsonStr)
            Log.i(TAG, "Injected ${packages.size} packages into Mihomo PackageManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject package list", e)
        }
    }

    private fun ensureGlobalDefaultNode() {
        serviceScope.launch {
            try {
                val req = Request.Builder().url("http://127.0.0.1:9090/proxies/GLOBAL").get().build()
                httpClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use
                    val obj = JSONObject(res.body?.string() ?: return@use)
                    val now = obj.optString("now", "")
                    if (now == "DIRECT" || now == "REJECT" || now == "EasyTier-内网桥接" || now.isBlank()) {
                        val allArr = obj.optJSONArray("all") ?: return@use
                        var candidate = ""
                        for (i in 0 until allArr.length()) {
                            val item = allArr.optString(i)
                            if (item !in setOf("DIRECT", "REJECT", "EasyTier-内网桥接", "GLOBAL") && item.isNotBlank()) {
                                candidate = item
                                break
                            }
                        }
                        if (candidate.isNotBlank()) {
                            val bodyStr = JSONObject().put("name", candidate).toString()
                            val putReq = Request.Builder()
                                .url("http://127.0.0.1:9090/proxies/GLOBAL")
                                .put(bodyStr.toRequestBody("application/json".toMediaType()))
                                .build()
                            httpClient.newCall(putReq).execute().close()
                            Log.i(TAG, "Auto-selected default GLOBAL proxy node: $candidate")
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun ensureTrafficPollingStarted() {
        if (trafficPollingJob?.isActive == true) return
        trafficPollingJob = serviceScope.launch {
            var tick = 0L
            while (isActive && (_isRunning.value || EasyTierRepository.runtimeState.value.isRunning)) {
                // Check if VPN was revoked by system without onRevoke being dispatched yet
                if (_isRunning.value && VpnService.prepare(this@UnifiedVpnService) != null) {
                    Log.w(TAG, "Detected VPN permission revoked during traffic polling (another VPN app active)")
                    handleVpnRevoked()
                    break
                }

                val clashRunning = _isRunning.value
                val upSpeed = if (clashRunning) Mobile.queryTrafficNowUp() else 0L
                val downSpeed = if (clashRunning) Mobile.queryTrafficNowDown() else 0L
                val upTotal = if (clashRunning) Mobile.queryTrafficUp() else 0L
                val downTotal = if (clashRunning) Mobile.queryTrafficDown() else 0L

                val etState = EasyTierRepository.runtimeState.value
                val etRunning = etState.isRunning
                val etUpSpeed = if (etRunning) etState.txSpeed else 0L
                val etDownSpeed = if (etRunning) etState.rxSpeed else 0L
                val etUpTotal = if (etRunning) etState.txTotalBytes else 0L
                val etDownTotal = if (etRunning) etState.rxTotalBytes else 0L

                tick++
                if (clashRunning && tick % 2 == 1L) {
                    try {
                        val req = Request.Builder().url("http://127.0.0.1:9090/configs").get().build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val json = JSONObject(res.body?.string() ?: "")
                                val rawM = json.optString("mode", "").lowercase()
                                val m = if (EasyTierRepository.getBridgeRoutingInfo(this@UnifiedVpnService).enabled &&
                                    ConfigManager.getConfiguredClashMode(this@UnifiedVpnService) == "global"
                                ) {
                                    "global"
                                } else {
                                    rawM
                                }
                                if (m.isNotEmpty()) {
                                    currentModeName = when (m) {
                                        "global" -> "全局"
                                        "direct" -> "直连"
                                        "rule" -> "规则"
                                        "script" -> "脚本"
                                        else -> m
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore API error
                    }
                }
                val stats = TrafficStats(
                    upSpeed = upSpeed,
                    downSpeed = downSpeed,
                    upTotal = upTotal,
                    downTotal = downTotal,
                    easyTierUpSpeed = etUpSpeed,
                    easyTierDownSpeed = etDownSpeed,
                    easyTierUpTotal = etUpTotal,
                    easyTierDownTotal = etDownTotal,
                    tick = tick
                )
                _trafficStats.value = stats
                if (AppSettingsRepository.isShowSpeedNotification(this@UnifiedVpnService)) {
                    updateNotification(stats)
                } else {
                    cancelNotification()
                }
                delay(1000)
            }
            if (!_isRunning.value && !EasyTierRepository.runtimeState.value.isRunning && !isRestartingVpn) {
                stopTrafficPollingAndNotification()
            }
        }
    }

    private fun stopTrafficPollingAndNotification() {
        trafficPollingJob?.cancel()
        trafficPollingJob = null
        _trafficStats.value = TrafficStats()
        cancelNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VPN 状态与实时网速",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示 VPN 运行状态及当前上下行实时网速"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(stats: TrafficStats): Notification {
        val clashRunning = _isRunning.value
        val etState = EasyTierRepository.runtimeState.value
        val etRunning = etState.isRunning

        val activeProfile = ProfileRepository.getActiveProfile() ?: ProfileRepository.profiles.value.firstOrNull()
        val profileName = activeProfile?.name?.ifBlank { null } ?: "默认配置"

        val etRawIp = etState.allocatedIpv4.substringBefore("/").trim()
        val etIpDisplay = when {
            etRawIp.isNotEmpty() && etRawIp[0].isDigit() -> etRawIp
            etState.allocatedIpv4.contains("DHCP") -> "DHCP中"
            else -> etState.networkName.ifBlank { "组网中" }
        }

        val clashDownStr = BandwidthFormatter.formatAdaptive(stats.downSpeed)
        val clashUpStr = BandwidthFormatter.formatAdaptive(stats.upSpeed)
        val clashDownTotalStr = BandwidthFormatter.formatTotalBytes(stats.downTotal)
        val clashUpTotalStr = BandwidthFormatter.formatTotalBytes(stats.upTotal)

        val etDownStr = BandwidthFormatter.formatAdaptive(stats.easyTierDownSpeed)
        val etUpStr = BandwidthFormatter.formatAdaptive(stats.easyTierUpSpeed)
        val etDownTotalStr = BandwidthFormatter.formatTotalBytes(stats.easyTierDownTotal)
        val etUpTotalStr = BandwidthFormatter.formatTotalBytes(stats.easyTierUpTotal)

        val titleText: String
        val contentText: String
        val bigText: String

        when {
            clashRunning && etRunning -> {
                titleText = "Mihomo($profileName) + EasyTier($etIpDisplay)"
                contentText = "↓$clashDownStr ↑$clashUpStr | ↓$etDownStr ↑$etUpStr"
                bigText = "Mihomo: ↓ $clashDownStr   ↑ $clashUpStr (累计 ↓$clashDownTotalStr ↑$clashUpTotalStr)\n" +
                    "EasyTier: ↓ $etDownStr   ↑ $etUpStr (累计 ↓$etDownTotalStr ↑$etUpTotalStr)"
            }
            !clashRunning && etRunning -> {
                val netName = etState.networkName.ifBlank { "easytier" }
                titleText = "EasyTier · $netName ($etIpDisplay)"
                contentText = "↓ $etDownStr   ↑ $etUpStr   ·   ${etState.peers.size} 节点"
                bigText = "实时网速: ↓ $etDownStr   ↑ $etUpStr\n" +
                    "累计流量: ↓ $etDownTotalStr   ↑ $etUpTotalStr   ·   在线节点: ${etState.peers.size}"
            }
            else -> {
                titleText = "Mihomo · $profileName ($currentModeName)"
                contentText = "↓ $clashDownStr   ↑ $clashUpStr"
                bigText = "实时网速: ↓ $clashDownStr   ↑ $clashUpStr\n" +
                    "累计流量: ↓ $clashDownTotalStr   ↑ $clashUpTotalStr"
            }
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mihotier)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(stats: TrafficStats) {
        try {
            createNotificationChannel()
            val notification = buildNotification(stats)
            if (!isForegroundActive) {
                try {
                    startForeground(NOTIFICATION_ID, notification)
                    isForegroundActive = true
                } catch (e: Exception) {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification", e)
        }
    }

    private fun cancelNotification() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // ignore
        }
        isForegroundActive = false
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel notification", e)
        }
    }

    private suspend fun stopEasyTierStandaloneTun() {
        tunMutex.lock()
        try {
            stopEasyTierStandaloneTunLocked()
        } finally {
            tunMutex.unlock()
        }
    }

    private fun stopEasyTierStandaloneTunLocked() {
        if (_isEasyTierTunActive.value || easyTierVpnInterface != null) {
            Log.i(TAG, "Stopping EasyTier Standalone TUN")
            _isEasyTierTunActive.value = false
            appliedEasyTierStandaloneSignature = ""
            try {
                EasyTierNative.nativeSetTunFd(0)
            } catch (e: Throwable) {
                Log.w(TAG, "nativeSetTunFd(0) error", e)
            }
            try {
                easyTierVpnInterface?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing easyTierVpnInterface", e)
            }
            easyTierVpnInterface = null
        }
    }

    private suspend fun syncEasyTierStandaloneTun() {
        tunMutex.lock()
        try {
            if (_isRunning.value || isRestartingVpn) return
            val cfg = EasyTierRepository.getStandaloneTunConfig(this@UnifiedVpnService)
            if (!cfg.enabled) {
                stopEasyTierStandaloneTunLocked()
                return
            }
            val sig = "${cfg.localIp}/${cfg.prefixLength}:${cfg.mtu}:${cfg.routes.joinToString(",") { "${it.first}/${it.second}" }}"
            if (_isEasyTierTunActive.value && easyTierVpnInterface != null && sig == appliedEasyTierStandaloneSignature) {
                return
            }
            if (VpnService.prepare(this@UnifiedVpnService) != null) {
                Log.w(TAG, "VPN permission not granted yet; waiting for user approval before establishing EasyTier Standalone TUN")
                return
            }
            stopEasyTierStandaloneTunLocked()
            try {
                val builder = Builder()
                    .setSession("EasyTier VPN")
                    .setMtu(cfg.mtu)
                    .addAddress(cfg.localIp, cfg.prefixLength)
                for ((addr, prefix) in cfg.routes) {
                    try {
                        builder.addRoute(addr, prefix)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add EasyTier standalone route $addr/$prefix", e)
                    }
                }
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to disallow self package on EasyTier TUN", e)
                }
                val pfd = builder.establish()
                if (pfd != null) {
                    easyTierVpnInterface = pfd
                    val fd = pfd.fd
                    val res = EasyTierNative.nativeSetTunFd(fd)
                    appliedEasyTierStandaloneSignature = sig
                    _isEasyTierTunActive.value = true
                    Log.i(TAG, "EasyTier Standalone TUN established: ip=${cfg.localIp}/${cfg.prefixLength}, routes=${cfg.routes}, fd=$fd, attachRes=$res")
                } else {
                    Log.e(TAG, "builder.establish() returned null for EasyTier Standalone TUN")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to establish EasyTier Standalone TUN", e)
            }
        } finally {
            tunMutex.unlock()
        }
    }

    private fun stopVpn() {
        if (!_isRunning.value) return
        serviceScope.launch {
            stopVpnInternal(shouldStopSelf = true)
        }
    }

    private suspend fun stopVpnInternal(shouldStopSelf: Boolean) {
        if (!_isRunning.value) return
        _isRunning.value = false

        try {
            // Stop Mihomo - Mobile.stop() will also close the fd on the Go side,
            // since we transferred ownership via detachFd().
            Mobile.stop()
            Log.i(TAG, "Mihomo core stopped")

            // fd ownership was transferred to Go via detachFd(), so do NOT call
            // vpnInterface.close() here (the fd is already closed by Go).
            // Just null out the reference so Android knows the VPN is down.
            vpnInterface = null
            Log.i(TAG, "VPN Tunnel closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }

        if (shouldStopSelf) {
            if (EasyTierRepository.runtimeState.value.isRunning && VpnService.prepare(this@UnifiedVpnService) == null) {
                syncEasyTierStandaloneTun()
                ensureTrafficPollingStarted()
            } else {
                stopTrafficPollingAndNotification()
                stopSelf()
            }
        }
    }

    override fun onRevoke() {
        Log.i(TAG, "onRevoke: VPN service revoked by system (another VPN app started or VPN permission revoked)")
        handleVpnRevoked()
        super.onRevoke()
    }

    private fun handleVpnRevoked() {
        if (!_isRunning.value && !_isEasyTierTunActive.value && !isForegroundActive) return
        Log.i(TAG, "Handling VPN revoke: stopping Mihomo core, clearing notification, and stopping service")
        _isRunning.value = false
        _isEasyTierTunActive.value = false
        appliedEasyTierStandaloneSignature = ""

        stopTrafficPollingAndNotification()

        try {
            EasyTierNative.nativeSetTunFd(0)
        } catch (e: Throwable) {
            Log.w(TAG, "nativeSetTunFd(0) error on revoke", e)
        }
        try {
            easyTierVpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing easyTierVpnInterface on revoke", e)
        }
        easyTierVpnInterface = null

        try {
            Mobile.stop()
            Log.i(TAG, "Mihomo core stopped on revoke")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Mihomo on revoke", e)
        }
        vpnInterface = null

        cancelNotification()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        try {
            Mobile.stop()
        } catch (_: Exception) {}
        vpnInterface = null
        runBlocking {
            stopEasyTierStandaloneTun()
        }
        cancelNotification()
        serviceScope.cancel()
    }

    override fun protect(fd: Int): Boolean {
        Log.d(TAG, "Protecting socket fd: $fd")
        return super.protect(fd)
    }

    companion object {
        const val TAG = "UnifiedVpnService"
        const val ACTION_START_VPN = "top.xing2387.mihotier.action.START"
        const val ACTION_STOP_VPN = "top.xing2387.mihotier.action.STOP"
        const val ACTION_RESTART_VPN = "top.xing2387.mihotier.action.RESTART"
        const val ACTION_SYNC_EASYTIER_TUN = "top.xing2387.mihotier.action.SYNC_EASYTIER_TUN"
        const val NOTIFICATION_CHANNEL_ID = "mihotier_vpn_traffic"
        const val NOTIFICATION_ID = 1001
    }
}
