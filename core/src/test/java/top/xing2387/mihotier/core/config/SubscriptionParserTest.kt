package top.xing2387.mihotier.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {

    @Test
    fun testDetectFormatYaml() {
        val yaml = """
            port: 7890
            mode: rule
            proxies:
              - name: test
                type: direct
            rules:
              - MATCH,DIRECT
        """.trimIndent()

        assertEquals(SubscriptionParser.ContentFormat.YAML, SubscriptionParser.detectFormat(yaml))
    }

    @Test
    fun testDetectFormatNodeList() {
        val nodes = """
            vless://00000000-0000-0000-0000-000000000000@example.com:443?type=ws&security=tls#Node1
            hysteria2://dummy_password@example.com:35000?sni=example.com#Node2
        """.trimIndent()

        assertEquals(SubscriptionParser.ContentFormat.NODE_LIST, SubscriptionParser.detectFormat(nodes))
    }

    @Test
    fun testDetectFormatBase64() {
        val nodes = """
            vless://00000000-0000-0000-0000-000000000000@example.com:443?type=ws&security=tls#Node1
            hysteria2://dummy_password@example.com:35000?sni=example.com#Node2
        """.trimIndent()
        val base64 = java.util.Base64.getEncoder().encodeToString(nodes.toByteArray())

        assertEquals(SubscriptionParser.ContentFormat.BASE64, SubscriptionParser.detectFormat(base64))
    }

    @Test
    fun testParseNodesToClashYaml() {
        val sampleNodes = """
            vless://00000000-0000-0000-0000-000000000001@example-ws.org:443?type=ws&encryption=none&host=example-ws.org&path=%2Fws&security=tls&fp=chrome&sni=example-ws.org#Node-WS
            vless://00000000-0000-0000-0000-000000000002@example-reality.org:443?type=tcp&security=reality&flow=xtls-rprx-vision&fp=chrome&sni=reality.example.org&pbk=MOCK_PUBLIC_KEY_1234567890abcdef&sid=mock1234#Node-Reality
            hysteria2://mock_hy2_password@example-hy2.org:35000/?sni=example-hy2.org&mport=35000-39000#Node-HY2
            trojan://mock_trojan_password@example-trojan.org:443?sni=example-trojan.org#Node-Trojan
            ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@example-ss.org:8388#Node-SS
        """.trimIndent()

        val base64 = java.util.Base64.getEncoder().encodeToString(sampleNodes.toByteArray())
        val generatedYaml = SubscriptionParser.parseOrConvert(base64)

        assertTrue(generatedYaml.contains("mixed-port: 7890"))
        assertTrue(generatedYaml.contains("Node-WS"))
        assertTrue(generatedYaml.contains("Node-Reality"))
        assertTrue(generatedYaml.contains("Node-HY2"))
        assertTrue(generatedYaml.contains("Node-Trojan"))
        assertTrue(generatedYaml.contains("Node-SS"))
        assertTrue(generatedYaml.contains("reality-opts:"))
        assertTrue(generatedYaml.contains("public-key: \"MOCK_PUBLIC_KEY_1234567890abcdef\""))
        assertTrue(generatedYaml.contains("short-id: \"mock1234\""))
        assertTrue(generatedYaml.contains("ports: \"35000-39000\""))
        assertTrue(generatedYaml.contains("proxy-groups:"))
        assertTrue(generatedYaml.contains("节点选择"))
        assertTrue(generatedYaml.contains("自动选择"))
        assertTrue(generatedYaml.contains("rules:"))
    }

    @Test
    fun testBase64ClashYamlPassthrough() {
        val rawYaml = """
            mixed-port: 7890
            mode: rule
            proxies:
              - name: "DirectNode"
                type: direct
            rules:
              - MATCH,DIRECT
        """.trimIndent()
        val base64Yaml = java.util.Base64.getEncoder().encodeToString(rawYaml.toByteArray())

        val detected = SubscriptionParser.detectFormat(base64Yaml)
        assertEquals(SubscriptionParser.ContentFormat.BASE64, detected)

        val parsed = SubscriptionParser.parseOrConvert(base64Yaml)
        assertTrue(parsed.contains("DirectNode"))
        assertTrue(parsed.contains("mixed-port: 7890"))
    }
}
