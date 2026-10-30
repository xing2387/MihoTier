package top.xing2387.mihotier

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.xing2387.mihotier.core.config.AppSettingsRepository
import top.xing2387.mihotier.core.vpn.UnifiedVpnService
import top.xing2387.mihotier.ui.screens.AppSelectionScreen
import top.xing2387.mihotier.ui.screens.AppSettingsScreen
import top.xing2387.mihotier.ui.screens.ClashAppSelectionScreen
import top.xing2387.mihotier.ui.screens.ClashConnectionsScreen
import top.xing2387.mihotier.ui.screens.ClashLogsScreen
import top.xing2387.mihotier.ui.screens.ClashMainScreen
import top.xing2387.mihotier.ui.screens.ClashMetaFeaturesScreen
import top.xing2387.mihotier.ui.screens.ClashNetworkSettingsScreen
import top.xing2387.mihotier.ui.screens.ClashOverrideSettingsScreen
import top.xing2387.mihotier.ui.screens.ClashProfileDetailScreen
import top.xing2387.mihotier.ui.screens.ClashProfilesScreen
import top.xing2387.mihotier.ui.screens.ClashProxyScreen
import top.xing2387.mihotier.ui.screens.ClashQrScannerScreen
import top.xing2387.mihotier.ui.screens.ClashSettingsScreen
import top.xing2387.mihotier.ui.screens.EasyTierLogsScreen
import top.xing2387.mihotier.ui.screens.EasyTierScreen
import top.xing2387.mihotier.ui.theme.MihotierTheme

class MainActivity : ComponentActivity() {

    private var vpnService: UnifiedVpnService? = null
    private var isBound = false

    private val _isVpnRunning = MutableStateFlow(false)
    val isVpnRunning = _isVpnRunning.asStateFlow()

    private val _trafficStats = MutableStateFlow(UnifiedVpnService.TrafficStats())
    val trafficStats = _trafficStats.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as? UnifiedVpnService.LocalBinder ?: return
            val svc = binder.getService()
            vpnService = svc
            isBound = true

            // If another VPN is currently holding the system VPN permission, reset running state
            if (VpnService.prepare(this@MainActivity) != null && svc.isRunning.value) {
                _isVpnRunning.value = false
                Intent(this@MainActivity, UnifiedVpnService::class.java).apply {
                    action = UnifiedVpnService.ACTION_STOP_VPN
                }.also { startService(it) }
            }

            // Observe the flows
            lifecycleScope.launch {
                vpnService?.isRunning?.collect { _isVpnRunning.value = it }
            }
            lifecycleScope.launch {
                vpnService?.trafficStats?.collect { _trafficStats.value = it }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            vpnService = null
            _isVpnRunning.value = false
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, UnifiedVpnService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    fun toggleVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 100)
        } else {
            vpnService?.toggleVpn()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            vpnService?.toggleVpn()
        }
    }

    override fun onResume() {
        super.onResume()
        AppSettingsRepository.applyExcludeFromRecents(this)
        checkVpnStatus()
    }

    private fun checkVpnStatus() {
        if (_isVpnRunning.value && VpnService.prepare(this) != null) {
            _isVpnRunning.value = false
            Intent(this, UnifiedVpnService::class.java).apply {
                action = UnifiedVpnService.ACTION_STOP_VPN
            }.also { startService(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettingsRepository.applyExcludeFromRecents(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        setContent {
            MihotierTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "app_selection") {
                    composable("app_selection") {
                        val isRunning by isVpnRunning.collectAsState()
                        val stats by trafficStats.collectAsState()

                        AppSelectionScreen(
                            isRunning = isRunning,
                            trafficStats = stats,
                            onToggleVpn = { toggleVpn() },
                            onNavigateToClash = { navController.navigate("clash") },
                            onNavigateToEasyTier = { navController.navigate("easytier") },
                            onNavigateToProxy = { navController.navigate("clash_proxy") },
                            onNavigateToProfiles = { navController.navigate("clash_profiles") },
                            onNavigateToOverride = { navController.navigate("clash_settings_override") },
                            onNavigateToSettings = { navController.navigate("clash_settings") },
                            onNavigateToAppSettings = { navController.navigate("app_settings") }
                        )
                    }
                    composable("easytier") {
                        EasyTierScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToLogs = { navController.navigate("easytier_logs") }
                        )
                    }
                    composable("easytier_logs") {
                        EasyTierLogsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("clash") {
                        val isRunning by isVpnRunning.collectAsState()
                        val stats by trafficStats.collectAsState()

                        ClashMainScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToProxy = { navController.navigate("clash_proxy") },
                            onNavigateToProfiles = { navController.navigate("clash_profiles") },
                            onNavigateToSettings = { navController.navigate("clash_settings") },
                            onNavigateToConnections = { navController.navigate("clash_connections") },
                            onNavigateToLogs = { navController.navigate("clash_logs") },
                            isRunning = isRunning,
                            trafficStats = stats,
                            onToggleVpn = { toggleVpn() },
                            onRestartVpn = { vpnService?.restartVpn() }
                        )
                    }
                    composable("clash_connections") {
                        val isRunning by isVpnRunning.collectAsState()
                        ClashConnectionsScreen(
                            isRunning = isRunning,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("clash_logs") {
                        val isRunning by isVpnRunning.collectAsState()
                        ClashLogsScreen(
                            isRunning = isRunning,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("clash_proxy") {
                        ClashProxyScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("clash_profiles") {
                        ClashProfilesScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { id ->
                                if (id != null) {
                                    navController.navigate("clash_profile_detail?profileId=$id")
                                } else {
                                    navController.navigate("clash_profile_detail")
                                }
                            }
                        )
                    }
                    composable("clash_profile_detail?profileId={profileId}") { backStackEntry ->
                        val profileId = backStackEntry.arguments?.getString("profileId")
                        ClashProfileDetailScreen(
                            profileId = profileId,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToScanner = { navController.navigate("clash_qr_scanner") },
                            navController = navController
                        )
                    }
                    composable("clash_qr_scanner") {
                        ClashQrScannerScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onQrScanned = { result ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("scanned_qr_content", result)
                                navController.popBackStack()
                            }
                        )
                    }
                    composable("clash_settings") {
                        ClashSettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToNetwork = { navController.navigate("clash_settings_network") },
                            onNavigateToOverride = { navController.navigate("clash_settings_override") },
                            onNavigateToMetaFeatures = { navController.navigate("clash_settings_meta") }
                        )
                    }
                    composable("app_settings") {
                        AppSettingsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("clash_settings_override") {
                        val isRunning by isVpnRunning.collectAsState()
                        ClashOverrideSettingsScreen(
                            isRunning = isRunning,
                            onRestartVpn = { vpnService?.restartVpn() },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("clash_settings_meta") {
                        val isRunning by isVpnRunning.collectAsState()
                        ClashMetaFeaturesScreen(
                            isRunning = isRunning,
                            onRestartVpn = { vpnService?.restartVpn() },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("clash_settings_network") {
                        val isRunning by isVpnRunning.collectAsState()
                        ClashNetworkSettingsScreen(
                            isRunning = isRunning,
                            onRestartVpn = { vpnService?.restartVpn() },
                            onNavigateToAppSelection = { navController.navigate("clash_access_control_packages") },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("clash_access_control_packages") {
                        val isRunning by isVpnRunning.collectAsState()
                        ClashAppSelectionScreen(
                            isRunning = isRunning,
                            onRestartVpn = { vpnService?.restartVpn() },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}