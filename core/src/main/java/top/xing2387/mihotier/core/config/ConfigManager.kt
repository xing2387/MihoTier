package top.xing2387.mihotier.core.config

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class OriginalConfigSnapshot(
    val profileName: String,
    val hasProfileFile: Boolean,
    val rawYaml: String,
    val mixedPort: String?,
    val mode: String?,
    val allowLan: String?,
    val ipv6: String?,
    val logLevel: String?,
    val dnsEnabled: String?,
    val dnsEnhancedMode: String?,
    val dnsNameservers: List<String>,
    val dnsSectionYaml: String,
    val hostsSectionYaml: String,
    val hostsList: List<String>,
    val rulesList: List<String>,
    val findProcessMode: String? = null,
    val unifiedDelay: String? = null,
    val tcpConcurrent: String? = null,
    val globalClientFingerprint: String? = null,
    val geodataLoader: String? = null,
    val geodataMode: String? = null,
    val snifferEnabled: String? = null
)

object ConfigManager {
    private const val CONFIG_DIR_NAME = "config"

    private val DEFAULT_FALLBACK_YAML = """
        mixed-port: 7890
        allow-lan: true
        mode: rule
        log-level: info
        dns:
          enable: true
          listen: 0.0.0.0:1053
          enhanced-mode: fake-ip
          fake-ip-range: 198.18.0.1/16
          nameserver:
            - 114.114.114.114
            - 8.8.8.8
        rules:
          - MATCH,DIRECT
    """.trimIndent()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun getConfigDir(context: Context): File {
        val dir = File(context.filesDir, CONFIG_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get the un-overridden raw YAML config and parsed snapshot of the active profile.
     */
    fun getOriginalConfigSnapshot(context: Context): OriginalConfigSnapshot {
        ProfileRepository.init(context)
        val activeProfile = ProfileRepository.getActiveProfile()
        var rawYaml = DEFAULT_FALLBACK_YAML
        var hasFile = false

        if (activeProfile != null) {
            val file = File(getConfigDir(context), "${activeProfile.id}.yaml")
            if (file.exists()) {
                rawYaml = file.readText()
                hasFile = true
            }
        }

        val profileName = activeProfile?.name ?: "内置默认配置 (Default)"
        return parseOriginalSnapshot(profileName, hasFile, rawYaml)
    }

    private fun parseOriginalSnapshot(
        profileName: String,
        hasProfileFile: Boolean,
        rawYaml: String
    ): OriginalConfigSnapshot {
        fun extractTopScalar(key: String): String? {
            val regex = Regex("""(?m)^${Regex.escape(key)}\s*:\s*([^\n#]+)""")
            return regex.find(rawYaml)?.groupValues?.get(1)?.trim()?.trim('\'', '"')
        }

        val mixedPort = extractTopScalar("mixed-port") ?: extractTopScalar("port")
        val mode = extractTopScalar("mode")
        val allowLan = extractTopScalar("allow-lan")
        val ipv6 = extractTopScalar("ipv6")
        val logLevel = extractTopScalar("log-level")
        val findProcessMode = extractTopScalar("find-process-mode")
        val unifiedDelay = extractTopScalar("unified-delay")
        val tcpConcurrent = extractTopScalar("tcp-concurrent")
        val globalClientFingerprint = extractTopScalar("global-client-fingerprint")
        val geodataLoader = extractTopScalar("geodata-loader")
        val geodataMode = extractTopScalar("geodata-mode")

        val dnsBlock = extractTopLevelBlock(rawYaml, "dns")
        val hostsBlock = extractTopLevelBlock(rawYaml, "hosts")
        val rulesBlock = extractTopLevelBlock(rawYaml, "rules")
        val snifferBlock = extractTopLevelBlock(rawYaml, "sniffer")

        val dnsEnabled = Regex("""(?m)^\s+enable\s*:\s*([^\n#]+)""")
            .find(dnsBlock)?.groupValues?.get(1)?.trim()
        val dnsEnhancedMode = Regex("""(?m)^\s+enhanced-mode\s*:\s*([^\n#]+)""")
            .find(dnsBlock)?.groupValues?.get(1)?.trim()
        val snifferEnabled = Regex("""(?m)^\s+enable\s*:\s*([^\n#]+)""")
            .find(snifferBlock)?.groupValues?.get(1)?.trim()

        val dnsNameservers = parseYamlListUnderSubkey(dnsBlock, "nameserver")

        val hostsList = hostsBlock.lines()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains(":") }

        val rulesList = rulesBlock.lines()
            .drop(1)
            .map { it.trim() }
            .filter { it.startsWith("-") }
            .map { it.removePrefix("-").trim() }

        return OriginalConfigSnapshot(
            profileName = profileName,
            hasProfileFile = hasProfileFile,
            rawYaml = rawYaml,
            mixedPort = mixedPort,
            mode = mode,
            allowLan = allowLan,
            ipv6 = ipv6,
            logLevel = logLevel,
            dnsEnabled = dnsEnabled,
            dnsEnhancedMode = dnsEnhancedMode,
            dnsNameservers = dnsNameservers,
            dnsSectionYaml = dnsBlock,
            hostsSectionYaml = hostsBlock,
            hostsList = hostsList,
            rulesList = rulesList,
            findProcessMode = findProcessMode,
            unifiedDelay = unifiedDelay,
            tcpConcurrent = tcpConcurrent,
            globalClientFingerprint = globalClientFingerprint,
            geodataLoader = geodataLoader,
            geodataMode = geodataMode,
            snifferEnabled = snifferEnabled
        )
    }

    private fun extractTopLevelBlock(rawYaml: String, blockName: String): String {
        val lines = rawYaml.lines()
        val startIndex = lines.indexOfFirst {
            it.matches(Regex("""^${Regex.escape(blockName)}\s*:.*"""))
        }
        if (startIndex == -1) return ""

        val result = mutableListOf(lines[startIndex])
        for (i in (startIndex + 1) until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.add(line)
                continue
            }
            // A new top-level key starts at column 0 and is not a list item `-` at root unless inside rules
            val isIndented = line.startsWith(" ") || line.startsWith("\t")
            val isRootListItem = line.startsWith("-") && blockName == "rules"
            if (isIndented || isRootListItem) {
                result.add(line)
            } else {
                break
            }
        }
        return result.joinToString("\n").trimEnd()
    }

    private fun parseYamlListUnderSubkey(blockYaml: String, subkey: String): List<String> {
        if (blockYaml.isBlank()) return emptyList()
        val lines = blockYaml.lines()
        val idx = lines.indexOfFirst { it.trim().startsWith("$subkey:") }
        if (idx == -1) return emptyList()

        val inlinePart = lines[idx].substringAfter("$subkey:").trim()
        if (inlinePart.startsWith("[") && inlinePart.endsWith("]")) {
            return inlinePart.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().trim('\'', '"') }
                .filter { it.isNotEmpty() }
        }

        val items = mutableListOf<String>()
        for (i in (idx + 1) until lines.size) {
            val t = lines[i].trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            if (t.startsWith("-")) {
                items.add(t.removePrefix("-").trim().substringBefore("#").trim().trim('\'', '"'))
            } else {
                break
            }
        }
        return items
    }

    /**
     * Get the raw config yaml string for the active profile, with saved OverrideSettings applied.
     */
    fun getConfig(context: Context): String {
        val overrides = OverrideSettingsRepository.load(context)
        return generateOverriddenConfig(context, overrides)
    }

    /**
     * Get the user's configured Clash mode ("global", "rule", or "direct") before EasyTier rule-mode conversion.
     */
    fun getConfiguredClashMode(context: Context): String {
        ProfileRepository.init(context)
        val overrides = OverrideSettingsRepository.load(context)
        if (overrides.enableOverride && overrides.mode != "Keep" && overrides.mode.isNotBlank()) {
            return overrides.mode.trim().lowercase()
        }
        val activeProfile = ProfileRepository.getActiveProfile()
        var yaml = DEFAULT_FALLBACK_YAML
        if (activeProfile != null) {
            val file = File(getConfigDir(context), "${activeProfile.id}.yaml")
            if (file.exists()) {
                yaml = file.readText()
            }
        }
        return Regex("""(?m)^mode\s*:\s*(\w+)""").find(yaml)?.groupValues?.get(1)?.lowercase() ?: "rule"
    }

    /**
     * Generate the overridden YAML string for the active profile using the provided [overrides].
     */
    fun generateOverriddenConfig(context: Context, overrides: OverrideSettings): String {
        ProfileRepository.init(context)
        val activeProfile = ProfileRepository.getActiveProfile()
        var yaml = DEFAULT_FALLBACK_YAML

        if (activeProfile != null) {
            val file = File(getConfigDir(context), "${activeProfile.id}.yaml")
            if (file.exists()) {
                yaml = file.readText()
            }
        }

        if (overrides.enableOverride) {
            yaml = applyOverrides(yaml, overrides)
        }

        val meta = MetaFeaturesSettingsRepository.load(context)
        if (meta.enableMetaFeatures) {
            yaml = applyMetaFeatures(yaml, meta)
        }

        yaml = applyEasyTierBridge(context, yaml)

        return yaml
    }

    private fun applyEasyTierBridge(
        context: Context,
        rawYaml: String
    ): String {
        val bridgeInfo = EasyTierRepository.getBridgeRoutingInfo(context)
        if (!bridgeInfo.enabled || bridgeInfo.cidrs.isEmpty()) {
            return rawYaml
        }

        var yaml = rawYaml
        val proxyName = "EasyTier-内网桥接"
        val proxyItem = """{ name: "$proxyName", type: socks5, server: 127.0.0.1, port: ${bridgeInfo.socks5Port}, udp: true }"""

        if (!yaml.contains(proxyName)) {
            yaml = prependYamlSequenceItems(
                rawYaml = yaml,
                blockName = "proxies",
                newItems = listOf(proxyItem),
                insertBeforeRulesIfMissing = true
            )
        }

        val easyTierRules = bridgeInfo.cidrStrings.map { cidr ->
            "IP-CIDR,$cidr,$proxyName,no-resolve"
        }

        val globalModeRegex = Regex("""(?m)^mode\s*:\s*global\s*$""", RegexOption.IGNORE_CASE)
        val isGlobalMode = globalModeRegex.containsMatchIn(yaml)

        if (isGlobalMode) {
            // When Clash is in Global mode and EasyTier is running:
            // 1. Switch engine mode to `rule` so Mihomo evaluates the rule table
            // 2. Inject ONLY two rule groups: EasyTier subnet -> EasyTier-内网桥接, and MATCH -> GLOBAL
            yaml = globalModeRegex.replaceFirst(yaml, "mode: rule")
            val globalBridgeRules = easyTierRules + "MATCH,GLOBAL"
            yaml = replaceRulesBlock(yaml, globalBridgeRules)
        } else {
            val missingRules = easyTierRules.filter { rule -> !yaml.contains(rule) }
            if (missingRules.isNotEmpty()) {
                yaml = prependYamlSequenceItems(
                    rawYaml = yaml,
                    blockName = "rules",
                    newItems = missingRules,
                    insertBeforeRulesIfMissing = false
                )
            }
        }

        return yaml
    }

    private fun replaceRulesBlock(rawYaml: String, newRules: List<String>): String {
        val lines = rawYaml.lines()
        val startIndex = lines.indexOfFirst { it.matches(Regex("""^rules\s*:.*""")) }
        val newBlockLines = listOf("rules:") + newRules.map { "  - $it" }

        if (startIndex == -1) {
            return (lines + newBlockLines).joinToString("\n")
        }

        var endIndex = startIndex
        for (i in (startIndex + 1) until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                endIndex = i
                continue
            }
            val isIndented = line.startsWith(" ") || line.startsWith("\t")
            val isListItem = trimmed.startsWith("-")
            if (isIndented || isListItem) {
                endIndex = i
            } else {
                break
            }
        }

        val before = lines.subList(0, startIndex)
        val after = lines.subList(endIndex + 1, lines.size)
        return (before + newBlockLines + after).joinToString("\n")
    }

    private fun prependYamlSequenceItems(
        rawYaml: String,
        blockName: String,
        newItems: List<String>,
        insertBeforeRulesIfMissing: Boolean
    ): String {
        if (newItems.isEmpty()) return rawYaml
        val lines = rawYaml.lines().toMutableList()
        val headerRegex = Regex("""^${Regex.escape(blockName)}\s*:(?:\s*#.*|\s*\[\s*\])?\s*$""")
        val startIndex = lines.indexOfFirst { headerRegex.matches(it) }

        if (startIndex == -1) {
            val blockLines = listOf("$blockName:") + newItems.map { "  - $it" }
            if (insertBeforeRulesIfMissing) {
                val rulesIdx = lines.indexOfFirst { it.matches(Regex("""^rules\s*:.*""")) }
                if (rulesIdx != -1) {
                    lines.addAll(rulesIdx, blockLines)
                    return lines.joinToString("\n")
                }
            }
            lines.addAll(blockLines)
            return lines.joinToString("\n")
        }

        val headerLine = lines[startIndex]
        if (headerLine.contains("[") && headerLine.contains("]")) {
            lines[startIndex] = "$blockName:"
            lines.addAll(startIndex + 1, newItems.map { "  - $it" })
            return lines.joinToString("\n")
        }

        var indent = "  "
        var insertAt = startIndex + 1
        for (i in (startIndex + 1) until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("-")) {
                indent = line.takeWhile { it == ' ' || it == '\t' }
                insertAt = i
            }
            break
        }

        lines.addAll(insertAt, newItems.map { "$indent- $it" })
        return lines.joinToString("\n")
    }

    private fun applyMetaFeatures(rawYaml: String, meta: MetaFeaturesSettings): String {
        var yaml = rawYaml

        fun upsertScalarKey(key: String, value: String) {
            val regex = Regex("""(?m)^${Regex.escape(key)}\s*:.*$""")
            if (regex.containsMatchIn(yaml)) {
                yaml = regex.replaceFirst(yaml, "$key: $value")
            } else {
                yaml = "$key: $value\n$yaml"
            }
        }

        if (meta.findProcessMode != "Keep" && meta.findProcessMode.isNotBlank()) {
            upsertScalarKey("find-process-mode", meta.findProcessMode)
        }
        if (meta.unifiedDelay != "Keep" && meta.unifiedDelay.isNotBlank()) {
            upsertScalarKey("unified-delay", meta.unifiedDelay)
        }
        if (meta.tcpConcurrent != "Keep" && meta.tcpConcurrent.isNotBlank()) {
            upsertScalarKey("tcp-concurrent", meta.tcpConcurrent)
        }
        if (meta.globalClientFingerprint != "Keep" && meta.globalClientFingerprint.isNotBlank()) {
            upsertScalarKey("global-client-fingerprint", meta.globalClientFingerprint)
        }
        if (meta.geodataLoader != "Keep" && meta.geodataLoader.isNotBlank()) {
            upsertScalarKey("geodata-loader", meta.geodataLoader)
        }
        if (meta.geodataMode != "Keep" && meta.geodataMode.isNotBlank()) {
            upsertScalarKey("geodata-mode", meta.geodataMode)
        }
        if (meta.geoAutoUpdate) {
            upsertScalarKey("geo-auto-update", "true")
        }

        val profileBlockYaml = buildString {
            appendLine("profile:")
            appendLine("  store-selected: ${meta.storeSelected}")
            append("  store-fake-ip: ${meta.storeFakeIp}")
        }
        yaml = replaceOrInsertTopLevelBlock(yaml, "profile", profileBlockYaml)

        if (meta.snifferEnable) {
            val snifferBlockYaml = buildString {
                appendLine("sniffer:")
                appendLine("  enable: true")
                appendLine("  override-destination: ${meta.snifferOverrideDest}")
                appendLine("  force-dns-mapping: ${meta.snifferForceDnsMapping}")
                appendLine("  parse-pure-ip: ${meta.snifferParsePureIp}")
                appendLine("  sniff:")
                appendLine("    HTTP:")
                appendLine("      ports: [80, 8080-8880]")
                appendLine("      override-destination: true")
                appendLine("    TLS:")
                appendLine("      ports: [443, 8443]")
                appendLine("    QUIC:")
                append("      ports: [443, 8443]")
            }
            yaml = replaceOrInsertTopLevelBlock(yaml, "sniffer", snifferBlockYaml)
        }

        return yaml
    }

    /**
     * Export the overridden YAML config to a user-selected document [uri].
     */
    suspend fun exportOverriddenConfigToUri(
        context: Context,
        uri: Uri,
        overrides: OverrideSettings
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val yamlContent = generateOverriddenConfig(context, overrides)
                val bytes = yamlContent.toByteArray(Charsets.UTF_8)
                val outputStream = try {
                    context.contentResolver.openOutputStream(uri, "wt")
                } catch (_: Exception) {
                    context.contentResolver.openOutputStream(uri)
                }
                if (outputStream != null) {
                    outputStream.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    Result.success(bytes.size)
                } else {
                    Result.failure(Exception("无法打开目标路径写入流"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun replaceOrInsertTopLevelBlock(rawYaml: String, blockName: String, newBlockYaml: String): String {
        val lines = rawYaml.lines()
        val startIndex = lines.indexOfFirst {
            it.matches(Regex("""^${Regex.escape(blockName)}\s*:.*"""))
        }
        if (startIndex == -1) {
            return "$newBlockYaml\n$rawYaml"
        }

        var lastIndentedIndex = startIndex
        for (i in (startIndex + 1) until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val isIndented = line.startsWith(" ") || line.startsWith("\t")
            if (isIndented) {
                lastIndentedIndex = i
            } else if (trimmed.startsWith("#")) {
                continue
            } else {
                break
            }
        }

        val before = lines.subList(0, startIndex)
        val after = lines.subList(lastIndentedIndex + 1, lines.size)
        return (before + newBlockYaml.lines() + after).joinToString("\n")
    }

    private fun applyOverrides(rawYaml: String, overrides: OverrideSettings): String {
        var yaml = rawYaml

        fun upsertScalarKey(key: String, value: String) {
            val regex = Regex("""(?m)^${Regex.escape(key)}\s*:.*$""")
            if (regex.containsMatchIn(yaml)) {
                yaml = regex.replaceFirst(yaml, "$key: $value")
            } else {
                yaml = "$key: $value\n$yaml"
            }
        }

        val portNum = overrides.mixedPort.trim().toIntOrNull()
        if (portNum != null && portNum in 1..65535) {
            upsertScalarKey("mixed-port", portNum.toString())
        }

        if (overrides.mode != "Keep" && overrides.mode.isNotBlank()) {
            upsertScalarKey("mode", overrides.mode)
        }

        if (overrides.allowLan != "Keep" && overrides.allowLan.isNotBlank()) {
            upsertScalarKey("allow-lan", overrides.allowLan)
        }

        if (overrides.ipv6 != "Keep" && overrides.ipv6.isNotBlank()) {
            upsertScalarKey("ipv6", overrides.ipv6)
        }

        if (overrides.logLevel != "Keep" && overrides.logLevel.isNotBlank()) {
            upsertScalarKey("log-level", overrides.logLevel)
        }

        // Override DNS block if enabled
        if (overrides.overrideDns) {
            val nsList = overrides.nameservers.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("114.114.114.114", "223.5.5.5", "8.8.8.8") }
            val nsLines = nsList.joinToString("\n") { "    - $it" }
            val dnsBlockYaml = buildString {
                appendLine("dns:")
                appendLine("  enable: true")
                appendLine("  listen: 0.0.0.0:1053")
                appendLine("  enhanced-mode: fake-ip")
                appendLine("  fake-ip-range: 198.18.0.1/16")
                appendLine("  nameserver:")
                append(nsLines)
            }
            yaml = replaceOrInsertTopLevelBlock(yaml, "dns", dnsBlockYaml)
        }

        // Inject custom hosts
        if (overrides.enableCustomHosts) {
            val hostEntries = overrides.customHosts.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains(":") }
            if (hostEntries.isNotEmpty()) {
                val formattedHosts = hostEntries.joinToString("\n") { line ->
                    val domain = line.substringBefore(":").trim()
                    val ip = line.substringAfter(":").trim()
                    "  '$domain': $ip"
                }
                val hostsRegex = Regex("""(?m)^hosts\s*:\s*$""")
                if (hostsRegex.containsMatchIn(yaml)) {
                    yaml = hostsRegex.replaceFirst(yaml, "hosts:\n$formattedHosts")
                } else {
                    yaml = "hosts:\n$formattedHosts\n$yaml"
                }
            }
        }

        // Prepend custom rules right after `rules:`
        if (overrides.enableCustomRules) {
            val ruleEntries = overrides.customRules.lines()
                .map { it.trim().removePrefix("-").trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            if (ruleEntries.isNotEmpty()) {
                val formattedRules = ruleEntries.joinToString("\n") { "  - $it" }
                val rulesRegex = Regex("""(?m)^rules\s*:\s*$""")
                if (rulesRegex.containsMatchIn(yaml)) {
                    yaml = rulesRegex.replaceFirst(yaml, "rules:\n$formattedRules")
                } else {
                    yaml = "$yaml\nrules:\n$formattedRules\n"
                }
            }
        }

        return yaml
    }

    /**
     * Download config from a subscription URL and save it for a specific profile ID.
     */
    suspend fun downloadAndSaveConfig(context: Context, urlString: String, profileId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "clash.meta; Mihotier/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val configText = response.body?.string() ?: ""
                        val processedYaml = SubscriptionParser.parseOrConvert(configText)
                        saveConfigText(context, processedYaml, profileId)
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("HTTP Error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Copy a local file URI to the config directory for a specific profile ID.
     */
    suspend fun copyConfigFromUri(context: Context, uri: Uri, profileId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val configText = inputStream.bufferedReader().use { it.readText() }
                    val processedYaml = SubscriptionParser.parseOrConvert(configText)
                    saveConfigText(context, processedYaml, profileId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Unable to open file URI"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun saveConfigText(context: Context, configText: String, profileId: String) {
        val file = File(getConfigDir(context), "${profileId}.yaml")
        file.writeText(configText)
    }
}
