package top.xing2387.mihotier.core.config

import android.content.Context

data class OverrideSettings(
    val enableOverride: Boolean = true,
    val enableCustomRules: Boolean = false,
    val customRules: String = "# 每行一条规则，将优先插入到配置 rules 最前面\n# 示例：\n# DOMAIN-SUFFIX,google.com,PROXY\n# IP-CIDR,192.168.1.0/24,DIRECT,no-resolve\n",
    val enableCustomHosts: Boolean = false,
    val customHosts: String = "# 每行一条域名映射 (格式: 域名: IP)\n# 示例：\n# localhost: 127.0.0.1\n",
    val mixedPort: String = "",
    val mode: String = "Keep",       // "Keep", "rule", "global", "direct"
    val allowLan: String = "Keep",   // "Keep", "true", "false"
    val ipv6: String = "Keep",       // "Keep", "true", "false"
    val logLevel: String = "Keep",   // "Keep", "info", "warning", "error", "debug", "silent"
    val overrideDns: Boolean = true,
    val nameservers: String = "114.114.114.114, 223.5.5.5, 8.8.8.8"
)

object OverrideSettingsRepository {
    private const val PREFS_NAME = "clash_override_settings"

    fun load(context: Context): OverrideSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = OverrideSettings()
        return OverrideSettings(
            enableOverride = prefs.getBoolean("enable_override", defaults.enableOverride),
            enableCustomRules = prefs.getBoolean("enable_custom_rules", defaults.enableCustomRules),
            customRules = prefs.getString("custom_rules", defaults.customRules) ?: defaults.customRules,
            enableCustomHosts = prefs.getBoolean("enable_custom_hosts", defaults.enableCustomHosts),
            customHosts = prefs.getString("custom_hosts", defaults.customHosts) ?: defaults.customHosts,
            mixedPort = prefs.getString("mixed_port", defaults.mixedPort) ?: defaults.mixedPort,
            mode = prefs.getString("mode", defaults.mode) ?: defaults.mode,
            allowLan = prefs.getString("allow_lan", defaults.allowLan) ?: defaults.allowLan,
            ipv6 = prefs.getString("ipv6", defaults.ipv6) ?: defaults.ipv6,
            logLevel = prefs.getString("log_level", defaults.logLevel) ?: defaults.logLevel,
            overrideDns = prefs.getBoolean("override_dns", defaults.overrideDns),
            nameservers = prefs.getString("nameservers", defaults.nameservers) ?: defaults.nameservers
        )
    }

    fun save(context: Context, settings: OverrideSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("enable_override", settings.enableOverride)
            .putBoolean("enable_custom_rules", settings.enableCustomRules)
            .putString("custom_rules", settings.customRules)
            .putBoolean("enable_custom_hosts", settings.enableCustomHosts)
            .putString("custom_hosts", settings.customHosts)
            .putString("mixed_port", settings.mixedPort)
            .putString("mode", settings.mode)
            .putString("allow_lan", settings.allowLan)
            .putString("ipv6", settings.ipv6)
            .putString("log_level", settings.logLevel)
            .putBoolean("override_dns", settings.overrideDns)
            .putString("nameservers", settings.nameservers)
            .apply()
    }
}
