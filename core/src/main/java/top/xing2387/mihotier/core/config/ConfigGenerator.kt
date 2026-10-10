package top.xing2387.mihotier.core.config

object ConfigGenerator {
    /**
     * Injects EasyTier SOCKS5 outbound and routing rules into an existing Mihomo/Clash config.
     * 
     * In a real implementation, you would parse the YAML/JSON using a library like SnakeYAML or Moshi,
     * modify the tree, and serialize it back.
     * For this illustration, we use string interpolation/append.
     */
    fun generateUnifiedConfig(
        originalConfig: String,
        easyTierSocksPort: Int,
        easyTierCidr: String
    ): String {
        // Dummy implementation of injecting proxies and rules
        val easyTierOutbound = """
            |  - name: "easytier-outbound"
            |    type: socks5
            |    server: 127.0.0.1
            |    port: $easyTierSocksPort
        """.trimMargin()

        val easyTierRule = "  - IP-CIDR,$easyTierCidr,easytier-outbound,no-resolve"

        return """
            |proxies:
            |$easyTierOutbound
            |# ... original proxies ...
            |
            |rules:
            |$easyTierRule
            |# ... original rules ...
            |  - MATCH,DIRECT
        """.trimMargin()
    }
}
