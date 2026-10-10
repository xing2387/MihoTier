package top.xing2387.mihotier.core.config

import android.content.Context

data class NetworkSettings(
    val routeSystemTraffic: Boolean = true,
    val bypassPrivateNetwork: Boolean = true,
    val dnsHijacking: Boolean = true,
    val allowBypass: Boolean = true,
    val allowIpv6: Boolean = false,
    val systemProxy: Boolean = true,
    val stackMode: String = "System Stack",
    val accessControlMode: String = "Allow all apps",
    val accessControlPackages: Set<String> = emptySet()
)

object NetworkSettingsRepository {
    private const val PREFS_NAME = "clash_network_settings"

    fun load(context: Context): NetworkSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NetworkSettings(
            routeSystemTraffic = prefs.getBoolean("route_system_traffic", true),
            bypassPrivateNetwork = prefs.getBoolean("bypass_private_network", true),
            dnsHijacking = prefs.getBoolean("dns_hijacking", true),
            allowBypass = prefs.getBoolean("allow_bypass", true),
            allowIpv6 = prefs.getBoolean("allow_ipv6", false),
            systemProxy = prefs.getBoolean("system_proxy", true),
            stackMode = prefs.getString("stack_mode", "System Stack") ?: "System Stack",
            accessControlMode = prefs.getString("access_control_mode", "Allow all apps") ?: "Allow all apps",
            accessControlPackages = prefs.getStringSet("access_control_packages", emptySet())?.toSet() ?: emptySet()
        )
    }

    fun save(context: Context, settings: NetworkSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("route_system_traffic", settings.routeSystemTraffic)
            .putBoolean("bypass_private_network", settings.bypassPrivateNetwork)
            .putBoolean("dns_hijacking", settings.dnsHijacking)
            .putBoolean("allow_bypass", settings.allowBypass)
            .putBoolean("allow_ipv6", settings.allowIpv6)
            .putBoolean("system_proxy", settings.systemProxy)
            .putString("stack_mode", settings.stackMode)
            .putString("access_control_mode", settings.accessControlMode)
            .putStringSet("access_control_packages", settings.accessControlPackages)
            .apply()
    }
}
