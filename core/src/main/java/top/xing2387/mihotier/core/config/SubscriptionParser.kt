package top.xing2387.mihotier.core.config

import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Utility for detecting subscription content format and converting node lists or
 * Base64 subscriptions into valid Mihomo / Clash Meta YAML configurations.
 */
object SubscriptionParser {

    enum class ContentFormat {
        YAML,
        BASE64,
        NODE_LIST,
        UNKNOWN
    }

    private val CLASH_KEYWORDS_REGEX = Regex(
        """(?m)^(?:proxies|proxy-groups|rules|mixed-port|port|mode|dns|allow-lan|log-level)\s*:"""
    )

    private val NODE_SCHEME_PREFIXES = listOf(
        "vless://",
        "vmess://",
        "ss://",
        "ssr://",
        "trojan://",
        "hysteria2://",
        "hy2://",
        "hysteria://",
        "tuic://"
    )

    /**
     * Automatically detect the content format:
     * - [ContentFormat.YAML]: Native Clash YAML or JSON format
     * - [ContentFormat.NODE_LIST]: Plain-text list of node URLs
     * - [ContentFormat.BASE64]: Base64-encoded string (containing nodes or YAML)
     * - [ContentFormat.UNKNOWN]: Unrecognized format
     */
    fun detectFormat(rawInput: String): ContentFormat {
        val trimmed = rawInput.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) return ContentFormat.UNKNOWN

        // 1. Direct Clash YAML check
        if (isClashYaml(trimmed)) {
            return ContentFormat.YAML
        }

        // 2. Direct Node List check
        if (isNodeList(trimmed)) {
            return ContentFormat.NODE_LIST
        }

        // 3. Base64 check
        if (isBase64Candidate(trimmed)) {
            val decoded = decodeBase64(trimmed)
            if (decoded != null) {
                val decodedTrimmed = decoded.trim().removePrefix("\uFEFF")
                if (isClashYaml(decodedTrimmed) || isNodeList(decodedTrimmed)) {
                    return ContentFormat.BASE64
                }
            }
        }

        return ContentFormat.UNKNOWN
    }

    /**
     * Parses or converts any raw subscription content into a valid Clash Meta YAML configuration.
     * Handles:
     * 1. Direct Clash YAML configurations
     * 2. Base64-encoded Clash YAML configurations
     * 3. Base64-encoded node URL lists (vless, vmess, hysteria2, trojan, ss, etc.)
     * 4. Plain-text node URL lists
     */
    fun parseOrConvert(rawInput: String): String {
        val trimmed = rawInput.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) return rawInput

        // 1. Direct Clash YAML
        if (isClashYaml(trimmed)) {
            return trimmed
        }

        // 2. Plain-text Node List
        if (isNodeList(trimmed)) {
            return convertNodeListToClashYaml(trimmed)
        }

        // 3. Try Base64 decoding
        val decoded = decodeBase64(trimmed)
        if (decoded != null) {
            val decodedTrimmed = decoded.trim().removePrefix("\uFEFF")
            if (isClashYaml(decodedTrimmed)) {
                return decodedTrimmed
            }
            if (isNodeList(decodedTrimmed)) {
                return convertNodeListToClashYaml(decodedTrimmed)
            }
        }

        // Fallback: If not recognized as node list or YAML, return raw string
        return rawInput
    }

    fun isClashYaml(content: String): Boolean {
        if (content.startsWith("---")) return true
        return CLASH_KEYWORDS_REGEX.containsMatchIn(content)
    }

    fun isNodeList(content: String): Boolean {
        val validLines = content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
        if (validLines.isEmpty()) return false
        val nodeCount = validLines.count { isNodeLink(it) }
        return nodeCount > 0 && nodeCount >= (validLines.size + 1) / 2
    }

    private fun isNodeLink(line: String): Boolean {
        return NODE_SCHEME_PREFIXES.any { line.startsWith(it, ignoreCase = true) }
    }

    private fun isBase64Candidate(str: String): Boolean {
        val cleaned = cleanBase64String(str)
        if (cleaned.length < 8) return false
        // Must contain only base64 characters
        for (ch in cleaned) {
            val isAlphaNum = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9'
            val isBase64Sym = ch == '+' || ch == '/' || ch == '=' || ch == '-' || ch == '_'
            if (!isAlphaNum && !isBase64Sym) return false
        }
        // Base64 subscriptions rarely contain colons or spaces after cleaning
        return !cleaned.contains(":")
    }

    private fun cleanBase64String(str: String): String {
        return str.filter { !it.isWhitespace() }
    }

    fun decodeBase64(input: String): String? {
        val cleaned = cleanBase64String(input)
        if (cleaned.isEmpty()) return null

        val padded = when (cleaned.length % 4) {
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned
        }

        // 1. Try Java 8+ / Android API 26+ Base64
        try {
            val bytes = java.util.Base64.getDecoder().decode(padded)
            return String(bytes, StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            try {
                val bytes = java.util.Base64.getUrlDecoder().decode(padded)
                return String(bytes, StandardCharsets.UTF_8)
            } catch (_: Throwable) {}
        }

        // 2. Try Android SDK android.util.Base64 via reflection
        try {
            val base64Class = Class.forName("android.util.Base64")
            val decodeMethod = base64Class.getMethod("decode", String::class.java, Int::class.javaPrimitiveType)
            // DEFAULT (0) or URL_SAFE (8)
            val flags = 0 or 8
            val bytes = decodeMethod.invoke(null, padded, flags) as? ByteArray
            if (bytes != null) {
                return String(bytes, StandardCharsets.UTF_8)
            }
        } catch (_: Throwable) {}

        // 3. Pure Kotlin Base64 fallback (guarantees operation in all environments)
        try {
            val bytes = pureKotlinBase64Decode(padded)
            return String(bytes, StandardCharsets.UTF_8)
        } catch (_: Throwable) {}

        return null
    }

    private fun pureKotlinBase64Decode(src: String): ByteArray {
        val table = IntArray(128) { -1 }
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, c ->
            table[c.code] = i
        }
        table['-'.code] = 62
        table['_'.code] = 63

        val clean = src.filter { it != '=' }
        val outLen = (clean.length * 6) / 8
        val out = ByteArray(outLen)
        var buffer = 0
        var bits = 0
        var outIdx = 0

        for (c in clean) {
            val v = if (c.code < 128) table[c.code] else -1
            if (v < 0) continue
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                if (outIdx < outLen) {
                    out[outIdx++] = ((buffer shr bits) and 0xFF).toByte()
                }
            }
        }
        return if (outIdx == outLen) out else out.copyOf(outIdx)
    }

    /**
     * Converts a list of node URLs into a complete Mihomo / Clash Meta YAML configuration.
     */
    fun convertNodeListToClashYaml(content: String): String {
        val lines = content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }

        val proxies = mutableListOf<Map<String, Any>>()
        val usedNames = mutableSetOf<String>()

        for (line in lines) {
            val proxy = parseNodeUrl(line, usedNames)
            if (proxy != null) {
                proxies.add(proxy)
            }
        }

        if (proxies.isEmpty()) {
            throw IllegalArgumentException("未找到可解析的有效代理节点")
        }

        return generateClashYaml(proxies)
    }

    private fun parseNodeUrl(line: String, usedNames: MutableSet<String>): Map<String, Any>? {
        return when {
            line.startsWith("vless://", ignoreCase = true) -> parseVless(line, usedNames)
            line.startsWith("hysteria2://", ignoreCase = true) || line.startsWith("hy2://", ignoreCase = true) ->
                parseHysteria2(line, usedNames)
            line.startsWith("vmess://", ignoreCase = true) -> parseVmess(line, usedNames)
            line.startsWith("trojan://", ignoreCase = true) -> parseTrojan(line, usedNames)
            line.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(line, usedNames)
            else -> null
        }
    }

    private fun getUniqueName(rawName: String, fallback: String, usedNames: MutableSet<String>): String {
        val decoded = try {
            URLDecoder.decode(rawName, "UTF-8").trim()
        } catch (_: Exception) {
            rawName.trim()
        }
        val base = decoded.ifBlank { fallback }
        var candidate = base
        var count = 2
        while (usedNames.contains(candidate)) {
            candidate = "$base ($count)"
            count++
        }
        usedNames.add(candidate)
        return candidate
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        queryString.split("&").forEach { pair ->
            val idx = pair.indexOf("=")
            if (idx != -1) {
                val key = pair.substring(0, idx).trim()
                val value = try {
                    URLDecoder.decode(pair.substring(idx + 1).trim(), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(idx + 1).trim()
                }
                map[key] = value
            }
        }
        return map
    }

    private fun parseVless(line: String, usedNames: MutableSet<String>): Map<String, Any>? {
        try {
            var raw = line.substringAfter("://")
            var nodeName = ""
            if (raw.contains("#")) {
                nodeName = raw.substringAfter("#")
                raw = raw.substringBefore("#")
            }

            var queryMap = emptyMap<String, String>()
            if (raw.contains("?")) {
                queryMap = parseQueryParams(raw.substringAfter("?"))
                raw = raw.substringBefore("?")
            }

            if (!raw.contains("@")) return null
            val uuid = raw.substringBefore("@")
            val hostPort = raw.substringAfter("@")
            val (server, port) = parseHostPort(hostPort, 443)

            val name = getUniqueName(nodeName, "VLESS-$server:$port", usedNames)

            val proxy = mutableMapOf<String, Any>(
                "name" to name,
                "type" to "vless",
                "server" to server,
                "port" to port,
                "uuid" to uuid,
                "udp" to true
            )

            val network = queryMap["type"] ?: "tcp"
            val security = queryMap["security"] ?: "none"
            val flow = queryMap["flow"].orEmpty()
            val sni = queryMap["sni"] ?: queryMap["peer"].orEmpty()
            val fp = queryMap["fp"] ?: queryMap["client-fingerprint"].orEmpty()
            val insecure = queryMap["insecure"] in listOf("1", "true")

            if (security.equals("tls", ignoreCase = true)) {
                proxy["tls"] = true
                if (sni.isNotEmpty()) proxy["servername"] = sni
                if (fp.isNotEmpty()) proxy["client-fingerprint"] = fp
                if (insecure) proxy["skip-cert-verify"] = true
            } else if (security.equals("reality", ignoreCase = true)) {
                proxy["tls"] = true
                if (sni.isNotEmpty()) proxy["servername"] = sni
                if (fp.isNotEmpty()) proxy["client-fingerprint"] = fp
                val realityOpts = mutableMapOf<String, Any>()
                queryMap["pbk"]?.let { if (it.isNotEmpty()) realityOpts["public-key"] = it }
                queryMap["sid"]?.let { if (it.isNotEmpty()) realityOpts["short-id"] = it }
                queryMap["spx"]?.let { if (it.isNotEmpty()) realityOpts["spider-x"] = it }
                if (realityOpts.isNotEmpty()) {
                    proxy["reality-opts"] = realityOpts
                }
            }

            if (flow.isNotEmpty()) {
                proxy["flow"] = flow
            }

            when (network.lowercase()) {
                "ws" -> {
                    proxy["network"] = "ws"
                    val wsOpts = mutableMapOf<String, Any>()
                    queryMap["path"]?.let { if (it.isNotEmpty()) wsOpts["path"] = it }
                    queryMap["host"]?.let { if (it.isNotEmpty()) wsOpts["headers"] = mapOf("Host" to it) }
                    if (wsOpts.isNotEmpty()) proxy["ws-opts"] = wsOpts
                }
                "grpc" -> {
                    proxy["network"] = "grpc"
                    val grpcOpts = mutableMapOf<String, Any>()
                    (queryMap["serviceName"] ?: queryMap["path"])?.let {
                        if (it.isNotEmpty()) grpcOpts["grpc-service-name"] = it
                    }
                    if (grpcOpts.isNotEmpty()) proxy["grpc-opts"] = grpcOpts
                }
                "http", "h2" -> {
                    proxy["network"] = "http"
                    val httpOpts = mutableMapOf<String, Any>()
                    queryMap["path"]?.let { if (it.isNotEmpty()) httpOpts["path"] = listOf(it) }
                    queryMap["host"]?.let { if (it.isNotEmpty()) httpOpts["headers"] = mapOf("Host" to listOf(it)) }
                    if (httpOpts.isNotEmpty()) proxy["http-opts"] = httpOpts
                }
            }

            return proxy
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseHysteria2(line: String, usedNames: MutableSet<String>): Map<String, Any>? {
        try {
            var raw = line.substringAfter("://")
            var nodeName = ""
            if (raw.contains("#")) {
                nodeName = raw.substringAfter("#")
                raw = raw.substringBefore("#")
            }

            var queryMap = emptyMap<String, String>()
            if (raw.contains("?")) {
                queryMap = parseQueryParams(raw.substringAfter("?"))
                raw = raw.substringBefore("?")
            }

            if (!raw.contains("@")) return null
            val password = raw.substringBefore("@")
            var hostPort = raw.substringAfter("@")
            if (hostPort.contains("/")) {
                hostPort = hostPort.substringBefore("/")
            }
            val (server, port) = parseHostPort(hostPort, 443)

            val name = getUniqueName(nodeName, "HY2-$server:$port", usedNames)

            val proxy = mutableMapOf<String, Any>(
                "name" to name,
                "type" to "hysteria2",
                "server" to server,
                "port" to port,
                "password" to password,
                "udp" to true
            )

            val ports = queryMap["mport"] ?: queryMap["ports"].orEmpty()
            if (ports.isNotEmpty()) proxy["ports"] = ports

            val sni = queryMap["sni"].orEmpty()
            if (sni.isNotEmpty()) proxy["sni"] = sni

            val pinSHA256 = queryMap["pinSHA256"] ?: queryMap["fingerprint"].orEmpty()
            if (pinSHA256.isNotEmpty()) proxy["fingerprint"] = pinSHA256

            val insecure = queryMap["insecure"] in listOf("1", "true")
            if (insecure) proxy["skip-cert-verify"] = true

            val obfs = queryMap["obfs"].orEmpty()
            if (obfs.isNotEmpty()) {
                proxy["obfs"] = obfs
                queryMap["obfs-password"]?.let { if (it.isNotEmpty()) proxy["obfs-password"] = it }
            }

            return proxy
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseVmess(line: String, usedNames: MutableSet<String>): Map<String, Any>? {
        try {
            val base64Content = line.substringAfter("://").trim()
            val jsonStr = decodeBase64(base64Content) ?: return null
            val json = JSONObject(jsonStr)

            val server = json.optString("add")
            val port = json.optInt("port", 443)
            val uuid = json.optString("id")
            val alterId = json.optInt("aid", 0)
            val nodeName = json.optString("ps")
            val net = json.optString("net", "tcp")
            val tls = json.optString("tls", "")
            val host = json.optString("host", "")
            val path = json.optString("path", "")
            val sni = json.optString("sni", "")
            val fp = json.optString("fp", "")

            if (server.isEmpty() || uuid.isEmpty()) return null

            val name = getUniqueName(nodeName, "VMess-$server:$port", usedNames)

            val proxy = mutableMapOf<String, Any>(
                "name" to name,
                "type" to "vmess",
                "server" to server,
                "port" to port,
                "uuid" to uuid,
                "alterId" to alterId,
                "cipher" to "auto",
                "udp" to true
            )

            if (tls.equals("tls", ignoreCase = true)) {
                proxy["tls"] = true
                if (sni.isNotEmpty()) proxy["servername"] = sni
                if (fp.isNotEmpty()) proxy["client-fingerprint"] = fp
            }

            when (net.lowercase()) {
                "ws" -> {
                    proxy["network"] = "ws"
                    val wsOpts = mutableMapOf<String, Any>()
                    if (path.isNotEmpty()) wsOpts["path"] = path
                    if (host.isNotEmpty()) wsOpts["headers"] = mapOf("Host" to host)
                    if (wsOpts.isNotEmpty()) proxy["ws-opts"] = wsOpts
                }
                "grpc" -> {
                    proxy["network"] = "grpc"
                    val grpcOpts = mutableMapOf<String, Any>()
                    if (path.isNotEmpty()) grpcOpts["grpc-service-name"] = path
                    if (grpcOpts.isNotEmpty()) proxy["grpc-opts"] = grpcOpts
                }
                "http", "h2" -> {
                    proxy["network"] = "http"
                    val httpOpts = mutableMapOf<String, Any>()
                    if (path.isNotEmpty()) httpOpts["path"] = listOf(path)
                    if (host.isNotEmpty()) httpOpts["headers"] = mapOf("Host" to listOf(host))
                    if (httpOpts.isNotEmpty()) proxy["http-opts"] = httpOpts
                }
            }

            return proxy
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseTrojan(line: String, usedNames: MutableSet<String>): Map<String, Any>? {
        try {
            var raw = line.substringAfter("://")
            var nodeName = ""
            if (raw.contains("#")) {
                nodeName = raw.substringAfter("#")
                raw = raw.substringBefore("#")
            }

            var queryMap = emptyMap<String, String>()
            if (raw.contains("?")) {
                queryMap = parseQueryParams(raw.substringAfter("?"))
                raw = raw.substringBefore("?")
            }

            if (!raw.contains("@")) return null
            val password = raw.substringBefore("@")
            val hostPort = raw.substringAfter("@")
            val (server, port) = parseHostPort(hostPort, 443)

            val name = getUniqueName(nodeName, "Trojan-$server:$port", usedNames)

            val proxy = mutableMapOf<String, Any>(
                "name" to name,
                "type" to "trojan",
                "server" to server,
                "port" to port,
                "password" to password,
                "udp" to true
            )

            val sni = queryMap["sni"] ?: queryMap["peer"].orEmpty()
            if (sni.isNotEmpty()) proxy["sni"] = sni

            val insecure = queryMap["allowInsecure"] in listOf("1", "true") ||
                    queryMap["insecure"] in listOf("1", "true")
            if (insecure) proxy["skip-cert-verify"] = true

            val network = queryMap["type"] ?: "tcp"
            if (network.equals("ws", ignoreCase = true)) {
                proxy["network"] = "ws"
                val wsOpts = mutableMapOf<String, Any>()
                queryMap["path"]?.let { if (it.isNotEmpty()) wsOpts["path"] = it }
                queryMap["host"]?.let { if (it.isNotEmpty()) wsOpts["headers"] = mapOf("Host" to it) }
                if (wsOpts.isNotEmpty()) proxy["ws-opts"] = wsOpts
            }

            return proxy
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseShadowsocks(line: String, usedNames: MutableSet<String>): Map<String, Any>? {
        try {
            var raw = line.substringAfter("://")
            var nodeName = ""
            if (raw.contains("#")) {
                nodeName = raw.substringAfter("#")
                raw = raw.substringBefore("#")
            }

            var cipher = ""
            var password = ""
            var server = ""
            var port = 8388

            if (raw.contains("@")) {
                val userPart = raw.substringBefore("@")
                val hostPart = raw.substringAfter("@")
                val (s, p) = parseHostPort(hostPart, 8388)
                server = s
                port = p

                val decodedUser = decodeBase64(userPart) ?: userPart
                if (decodedUser.contains(":")) {
                    cipher = decodedUser.substringBefore(":")
                    password = decodedUser.substringAfter(":")
                }
            } else {
                val decodedFull = decodeBase64(raw) ?: raw
                if (decodedFull.contains("@")) {
                    val userPart = decodedFull.substringBefore("@")
                    val hostPart = decodedFull.substringAfter("@")
                    val (s, p) = parseHostPort(hostPart, 8388)
                    server = s
                    port = p
                    if (userPart.contains(":")) {
                        cipher = userPart.substringBefore(":")
                        password = userPart.substringAfter(":")
                    }
                }
            }

            if (server.isEmpty() || cipher.isEmpty()) return null

            val name = getUniqueName(nodeName, "SS-$server:$port", usedNames)

            return mapOf(
                "name" to name,
                "type" to "ss",
                "server" to server,
                "port" to port,
                "cipher" to cipher,
                "password" to password,
                "udp" to true
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseHostPort(hostPort: String, defaultPort: Int): Pair<String, Int> {
        return if (hostPort.startsWith("[")) {
            val closing = hostPort.indexOf("]")
            val host = hostPort.substring(1, closing)
            val rest = hostPort.substring(closing + 1)
            val port = rest.removePrefix(":").toIntOrNull() ?: defaultPort
            Pair(host, port)
        } else {
            val parts = hostPort.split(":")
            val host = parts[0]
            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: defaultPort else defaultPort
            Pair(host, port)
        }
    }

    private fun generateClashYaml(proxies: List<Map<String, Any>>): String {
        val proxyNames = proxies.map { it["name"].toString() }

        val sb = StringBuilder()
        sb.appendLine("mixed-port: 7890")
        sb.appendLine("allow-lan: false")
        sb.appendLine("mode: rule")
        sb.appendLine("log-level: info")
        sb.appendLine("unified-delay: true")
        sb.appendLine("tcp-concurrent: true")
        sb.appendLine("dns:")
        sb.appendLine("  enable: true")
        sb.appendLine("  listen: 0.0.0.0:1053")
        sb.appendLine("  enhanced-mode: fake-ip")
        sb.appendLine("  fake-ip-range: 198.18.0.1/16")
        sb.appendLine("  nameserver:")
        sb.appendLine("    - 223.5.5.5")
        sb.appendLine("    - 119.29.29.29")
        sb.appendLine("    - 8.8.8.8")
        sb.appendLine()

        sb.appendLine("proxies:")
        for (p in proxies) {
            sb.appendLine(formatProxyBlock(p))
        }
        sb.appendLine()

        sb.appendLine("proxy-groups:")
        sb.appendLine("  - name: \"节点选择\"")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - \"自动选择\"")
        sb.appendLine("      - \"DIRECT\"")
        for (name in proxyNames) {
            sb.appendLine("      - ${escapeYamlString(name)}")
        }

        sb.appendLine("  - name: \"自动选择\"")
        sb.appendLine("    type: url-test")
        sb.appendLine("    url: \"http://www.gstatic.com/generate_204\"")
        sb.appendLine("    interval: 300")
        sb.appendLine("    tolerance: 50")
        sb.appendLine("    proxies:")
        for (name in proxyNames) {
            sb.appendLine("      - ${escapeYamlString(name)}")
        }

        sb.appendLine("  - name: \"故障转移\"")
        sb.appendLine("    type: fallback")
        sb.appendLine("    url: \"http://www.gstatic.com/generate_204\"")
        sb.appendLine("    interval: 300")
        sb.appendLine("    proxies:")
        for (name in proxyNames) {
            sb.appendLine("      - ${escapeYamlString(name)}")
        }
        sb.appendLine()

        sb.appendLine("rules:")
        sb.appendLine("  - GEOIP,private,DIRECT,no-resolve")
        sb.appendLine("  - GEOIP,lan,DIRECT,no-resolve")
        sb.appendLine("  - GEOIP,CN,DIRECT")
        sb.appendLine("  - MATCH,节点选择")

        return sb.toString()
    }

    private fun formatProxyBlock(proxy: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.appendLine("  - name: ${escapeYamlString(proxy["name"].toString())}")
        for ((k, v) in proxy) {
            if (k == "name") continue
            when (v) {
                is String -> sb.appendLine("    $k: ${escapeYamlString(v)}")
                is Number, is Boolean -> sb.appendLine("    $k: $v")
                is Map<*, *> -> {
                    sb.appendLine("    $k:")
                    for ((subK, subV) in v) {
                        when (subV) {
                            is String -> sb.appendLine("      $subK: ${escapeYamlString(subV)}")
                            is Number, is Boolean -> sb.appendLine("      $subK: $subV")
                            is Map<*, *> -> {
                                sb.appendLine("      $subK:")
                                for ((nestedK, nestedV) in subV) {
                                    sb.appendLine("        $nestedK: ${escapeYamlString(nestedV.toString())}")
                                }
                            }
                            is List<*> -> {
                                val inline = subV.joinToString(", ") { escapeYamlString(it.toString()) }
                                sb.appendLine("      $subK: [$inline]")
                            }
                        }
                    }
                }
                is List<*> -> {
                    val inline = v.joinToString(", ") { escapeYamlString(it.toString()) }
                    sb.appendLine("    $k: [$inline]")
                }
            }
        }
        return sb.toString().trimEnd()
    }

    private fun escapeYamlString(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
