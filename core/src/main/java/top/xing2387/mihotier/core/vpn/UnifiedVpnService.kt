package top.xing2387.mihotier.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.xing2387.mihotier.core.config.AppSettingsRepository
import top.xing2387.mihotier.core.config.BandwidthFormatter
import top.xing2387.mihotier.core.config.ConfigGenerator
import top.xing2387.mihotier.easytier.EasyTierNative
import top.xing2387.mihotier.easytier.SocketProtectCallback
import mobile.Mobile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.xing2387.mihotier.core.config.ConfigManager
import top.xing2387.mihotier.core.config.NetworkSettingsRepository
import top.xing2387.mihotier.core.config.ProfileRepository
import java.util.concurrent.TimeUnit

class UnifiedVpnService : VpnService(), SocketProtectCallback {

    private var vpnInterface: ParcelFileDescriptor? = null
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    data class TrafficStats(
        val upSpeed: Long = 0L,
        val downSpeed: Long = 0L,
        val upTotal: Long = 0L,
        val downTotal: Long = 0L,
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

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trafficPollingJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): UnifiedVpnService = this@UnifiedVpnService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "UnifiedVpnService created")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_VPN) {
            startVpn()
        } else if (intent?.action == ACTION_STOP_VPN) {
            stopVpn()
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
        if (_isRunning.value) {
            stopVpn()
            serviceScope.launch {
                kotlinx.coroutines.delay(600)
                startVpn()
            }
        }
    }

    private fun startVpn() {
        if (_isRunning.value) return
        _isRunning.value = true

        serviceScope.launch {
            try {
                // 1. Setup EasyTier Socket Protection Callback
                // EasyTierNative.nativeSetProtectSocketCallback(this@UnifiedVpnService)

                // 2. Start EasyTier Core on local SOCKS5 port
                // val easyTierConfig = """{"network_name": "default", "network_secret": "default"}"""
                // EasyTierNative.nativeStartEasyTier(easyTierConfig)
                // Log.i(TAG, "EasyTier core started on local SOCKS5")

                // 3. Start Mihomo/Sing-box Core
                top.xing2387.mihotier.core.config.ProfileRepository.init(this@UnifiedVpnService)
                val configStr = ConfigManager.getConfig(this@UnifiedVpnService)
                val parsedMode = Regex("""(?m)^mode:\s*(\w+)""").find(configStr)?.groupValues?.get(1)?.lowercase() ?: "rule"
                currentModeName = when (parsedMode) {
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

                // Routes (Route System Traffic & Bypass Private Networks)
                if (netSettings.routeSystemTraffic) {
                    addRoutes(builder, netSettings.bypassPrivateNetwork)
                } else {
                    builder.addRoute("198.18.0.0", 15)
                }

                // Allow apps to bypass VPN
                if (netSettings.allowBypass) {
                    builder.allowBypass()
                }

                // System HTTP Proxy (Android Q+)
                if (netSettings.systemProxy && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        val port = Regex("""(?:mixed-port|port):\s*(\d+)""").find(configStr)?.groupValues?.get(1)?.toIntOrNull() ?: 7890
                        builder.setHttpProxy(android.net.ProxyInfo.buildDirectProxy("127.0.0.1", port))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set HTTP proxy info", e)
                    }
                }

                // Access Control (Per-app proxy)
                builder.addDisallowedApplication(packageName) // always exclude self
                when (netSettings.accessControlMode) {
                    "Disallow selected apps" -> {
                        for (pkg in netSettings.accessControlPackages) {
                            if (pkg != packageName) {
                                try {
                                    builder.addDisallowedApplication(pkg)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Cannot disallow $pkg", e)
                                }
                            }
                        }
                    }
                    "Allow selected apps" -> {
                        for (pkg in netSettings.accessControlPackages) {
                            if (pkg != packageName) {
                                try {
                                    builder.addAllowedApplication(pkg)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Cannot allow $pkg", e)
                                }
                            }
                        }
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
                        mobile.Mobile.startWithFd(filesDir.absolutePath, configStr, fd.toLong())
                        Log.i(TAG, "Mihomo started successfully!")
                    } catch (e: Exception) {
                        Log.e(TAG, "Mihomo startWithFd threw exception!", e)
                        throw e
                    }
                }

                // 4. Start Foreground Notification (if enabled in settings)
                if (AppSettingsRepository.isShowSpeedNotification(this@UnifiedVpnService)) {
                    updateNotification(0L, 0L)
                } else {
                    cancelNotification()
                }

                // 5. Start Traffic Polling
                startTrafficPolling()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN", e)
                stopVpn()
            }
        }
    }

    private fun addRoutes(builder: Builder, bypassPrivateNetwork: Boolean) {
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
    }

    private fun copyAssetsToHomeDir() {
        val assetsToCopy = listOf("geoip.metadb", "geosite.dat", "country.mmdb")
        for (assetName in assetsToCopy) {
            val outFile = java.io.File(filesDir, assetName)
            if (!outFile.exists()) {
                try {
                    assets.open(assetName).use { input ->
                        java.io.FileOutputStream(outFile).use { output ->
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
            val jsonArray = org.json.JSONArray()
            for (pkg in packages) {
                val obj = org.json.JSONObject()
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

    private fun startTrafficPolling() {
        trafficPollingJob?.cancel()
        trafficPollingJob = serviceScope.launch {
            var tick = 0L
            while (isActive && _isRunning.value) {
                val upSpeed = Mobile.queryTrafficNowUp()
                val downSpeed = Mobile.queryTrafficNowDown()
                val upTotal = Mobile.queryTrafficUp()
                val downTotal = Mobile.queryTrafficDown()
                tick++
                if (tick % 2 == 1L) {
                    try {
                        val req = Request.Builder().url("http://127.0.0.1:9090/configs").get().build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val json = JSONObject(res.body?.string() ?: "")
                                val m = json.optString("mode", "").lowercase()
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
                _trafficStats.value = TrafficStats(
                    upSpeed = upSpeed,
                    downSpeed = downSpeed,
                    upTotal = upTotal,
                    downTotal = downTotal,
                    tick = tick
                )
                if (AppSettingsRepository.isShowSpeedNotification(this@UnifiedVpnService)) {
                    updateNotification(upSpeed, downSpeed)
                } else {
                    cancelNotification()
                }
                delay(1000)
            }
        }
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

    private fun buildNotification(upSpeed: Long = 0L, downSpeed: Long = 0L): Notification {
        val contentText = "↓ ${BandwidthFormatter.formatAdaptive(downSpeed)}   ↑ ${BandwidthFormatter.formatAdaptive(upSpeed)}"

        val activeProfile = ProfileRepository.getActiveProfile() ?: ProfileRepository.profiles.value.firstOrNull()
        val profileName = activeProfile?.name?.ifBlank { null } ?: "默认配置"
        val titleText = "$profileName($currentModeName)"

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val iconRes = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_sys_download

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(upSpeed: Long, downSpeed: Long) {
        try {
            createNotificationChannel()
            val notification = buildNotification(upSpeed, downSpeed)
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
        if (isForegroundActive) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                // ignore
            }
            isForegroundActive = false
        }
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel notification", e)
        }
    }

    private fun stopVpn() {
        if (!_isRunning.value) return
        _isRunning.value = false

        trafficPollingJob?.cancel()
        trafficPollingJob = null
        _trafficStats.value = TrafficStats()
        cancelNotification()

        serviceScope.launch {
            try {
                // Stop Mihomo - Mobile.stop() will also close the fd on the Go side,
                // since we transferred ownership via detachFd().
                Mobile.stop()
                Log.i(TAG, "Mihomo core stopped")

                // Stop EasyTier
                // EasyTierNative.nativeStopEasyTier()
                // Log.i(TAG, "EasyTier core stopped")

                // fd ownership was transferred to Go via detachFd(), so do NOT call
                // vpnInterface.close() here (the fd is already closed by Go).
                // Just null out the reference so Android knows the VPN is down.
                vpnInterface = null
                Log.i(TAG, "VPN Tunnel closed")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping VPN", e)
            }
            
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
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
        const val NOTIFICATION_CHANNEL_ID = "mihotier_vpn_traffic"
        const val NOTIFICATION_ID = 1001
    }
}
