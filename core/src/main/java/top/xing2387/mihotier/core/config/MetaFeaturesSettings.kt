package top.xing2387.mihotier.core.config

import android.content.Context

data class MetaFeaturesSettings(
    val enableMetaFeatures: Boolean = true,
    // Sniffer
    val snifferEnable: Boolean = true,
    val snifferOverrideDest: Boolean = false,
    val snifferForceDnsMapping: Boolean = true,
    val snifferParsePureIp: Boolean = true,
    // Process & Connection
    val findProcessMode: String = "always",          // "Keep", "always", "strict", "off"
    val unifiedDelay: String = "Keep",               // "Keep", "true", "false"
    val tcpConcurrent: String = "Keep",              // "Keep", "true", "false"
    val globalClientFingerprint: String = "Keep",    // "Keep", "chrome", "firefox", "safari", "ios", "android", "edge", "random"
    // GeoData & Cache
    val geodataLoader: String = "memconservative",   // "Keep", "memconservative", "standard"
    val geodataMode: String = "Keep",                // "Keep", "true", "false"
    val geoAutoUpdate: Boolean = false,
    val storeSelected: Boolean = true,
    val storeFakeIp: Boolean = false
)

object MetaFeaturesSettingsRepository {
    private const val PREFS_NAME = "clash_meta_features_settings"

    fun load(context: Context): MetaFeaturesSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = MetaFeaturesSettings()
        return MetaFeaturesSettings(
            enableMetaFeatures = prefs.getBoolean("enable_meta_features", defaults.enableMetaFeatures),
            snifferEnable = prefs.getBoolean("sniffer_enable", defaults.snifferEnable),
            snifferOverrideDest = prefs.getBoolean("sniffer_override_dest", defaults.snifferOverrideDest),
            snifferForceDnsMapping = prefs.getBoolean("sniffer_force_dns_mapping", defaults.snifferForceDnsMapping),
            snifferParsePureIp = prefs.getBoolean("sniffer_parse_pure_ip", defaults.snifferParsePureIp),
            findProcessMode = prefs.getString("find_process_mode", defaults.findProcessMode) ?: defaults.findProcessMode,
            unifiedDelay = prefs.getString("unified_delay", defaults.unifiedDelay) ?: defaults.unifiedDelay,
            tcpConcurrent = prefs.getString("tcp_concurrent", defaults.tcpConcurrent) ?: defaults.tcpConcurrent,
            globalClientFingerprint = prefs.getString("global_client_fingerprint", defaults.globalClientFingerprint) ?: defaults.globalClientFingerprint,
            geodataLoader = prefs.getString("geodata_loader", defaults.geodataLoader) ?: defaults.geodataLoader,
            geodataMode = prefs.getString("geodata_mode", defaults.geodataMode) ?: defaults.geodataMode,
            geoAutoUpdate = prefs.getBoolean("geo_auto_update", defaults.geoAutoUpdate),
            storeSelected = prefs.getBoolean("store_selected", defaults.storeSelected),
            storeFakeIp = prefs.getBoolean("store_fake_ip", defaults.storeFakeIp)
        )
    }

    fun save(context: Context, settings: MetaFeaturesSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("enable_meta_features", settings.enableMetaFeatures)
            .putBoolean("sniffer_enable", settings.snifferEnable)
            .putBoolean("sniffer_override_dest", settings.snifferOverrideDest)
            .putBoolean("sniffer_force_dns_mapping", settings.snifferForceDnsMapping)
            .putBoolean("sniffer_parse_pure_ip", settings.snifferParsePureIp)
            .putString("find_process_mode", settings.findProcessMode)
            .putString("unified_delay", settings.unifiedDelay)
            .putString("tcp_concurrent", settings.tcpConcurrent)
            .putString("global_client_fingerprint", settings.globalClientFingerprint)
            .putString("geodata_loader", settings.geodataLoader)
            .putString("geodata_mode", settings.geodataMode)
            .putBoolean("geo_auto_update", settings.geoAutoUpdate)
            .putBoolean("store_selected", settings.storeSelected)
            .putBoolean("store_fake_ip", settings.storeFakeIp)
            .apply()
    }
}
