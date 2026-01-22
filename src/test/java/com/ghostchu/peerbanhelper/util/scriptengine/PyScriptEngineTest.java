package com.ghostchu.peerbanhelper.util.scriptengine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PyScriptEngine 单元测试
 * 通过执行实际脚本文件来测试 Jython 脚本引擎功能
 */
class PyScriptEngineTest {

    private PyScriptEngine engine;
    private static final String SCRIPT_BASE_PATH = "/testdata/scripts/python/";

    @BeforeEach
    void setUp() {
        engine = new PyScriptEngine();
    }

    /**
     * 从资源文件加载脚本内容
     */
    private String loadScript(String scriptName) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(SCRIPT_BASE_PATH + scriptName)) {
            if (is == null) {
                throw new IOException("Script not found: " + scriptName);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Nested
    @DisplayName("引擎属性测试")
    class EnginePropertiesTests {

        @Test
        @DisplayName("应返回正确的引擎名称")
        void shouldReturnCorrectEngineName() {
            assertEquals("Jython", engine.getEngineName());
        }

        @Test
        @DisplayName("应返回正确的文件扩展名")
        void shouldReturnCorrectFileExtension() {
            assertEquals(".py", engine.getFileExtension());
        }
    }

    @Nested
    @DisplayName("脚本元数据解析测试")
    class MetadataParsingTests {

        @Test
        @DisplayName("应正确解析脚本元数据")
        void shouldParseScriptMetadata() throws IOException {
            String script = loadScript("simple_return_true.py");
            CompiledScript compiled = engine.compileScript(null, "fallback", script);

            assertNotNull(compiled);
            assertEquals("简单返回 true", compiled.name());
            assertEquals("Test", compiled.author());
            assertEquals("1.0", compiled.version());
            assertTrue(compiled.cacheable());
            assertTrue(compiled.threadSafe());
        }

        @Test
        @DisplayName("应使用备用名称当 @NAME 不存在时")
        void shouldUseFallbackNameWhenNameNotPresent() {
            String script = "result = True";
            CompiledScript compiled = engine.compileScript(null, "fallbackName", script);

            assertNotNull(compiled);
            assertEquals("fallbackName", compiled.name());
        }

        @Test
        @DisplayName("应解析非线程安全和不可缓存的设置")
        void shouldParseNonThreadSafeAndNonCacheableSettings() throws IOException {
            String script = loadScript("complex_condition.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            assertFalse(compiled.cacheable());
            assertFalse(compiled.threadSafe());
        }
    }

    @Nested
    @DisplayName("简单返回值测试")
    class SimpleReturnValueTests {

        @Test
        @DisplayName("脚本应返回 true")
        void scriptShouldReturnTrue() throws IOException {
            String script = loadScript("simple_return_true.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Object result = compiled.execute(compiled.newEnv());
            assertEquals(true, result);
        }

        @Test
        @DisplayName("脚本应返回 false")
        void scriptShouldReturnFalse() throws IOException {
            String script = loadScript("simple_return_false.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Object result = compiled.execute(compiled.newEnv());
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("数字返回值测试")
    class NumberReturnValueTests {

        @Test
        @DisplayName("应根据参数返回数字 0（无操作）")
        void shouldReturnZeroForNoAction() throws IOException {
            String script = loadScript("return_numbers.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("action", "none");

            Object result = compiled.execute(env);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("应根据参数返回数字 1（封禁）")
        void shouldReturnOneForBan() throws IOException {
            String script = loadScript("return_numbers.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("action", "ban");

            Object result = compiled.execute(env);
            assertEquals(1, result);
        }

        @Test
        @DisplayName("应根据参数返回数字 2（跳过）")
        void shouldReturnTwoForSkip() throws IOException {
            String script = loadScript("return_numbers.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("action", "skip");

            Object result = compiled.execute(env);
            assertEquals(2, result);
        }
    }

    @Nested
    @DisplayName("字符串返回值测试")
    class StringReturnValueTests {

        @Test
        @DisplayName("应返回空字符串表示通过")
        void shouldReturnEmptyStringForPass() throws IOException {
            String script = loadScript("return_string.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("returnType", "empty");
            env.put("clientName", "");

            Object result = compiled.execute(env);
            assertEquals("", result);
        }

        @Test
        @DisplayName("应返回 @ 开头字符串表示跳过")
        void shouldReturnAtPrefixedStringForSkip() throws IOException {
            String script = loadScript("return_string.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("returnType", "skip");
            env.put("clientName", "");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).startsWith("@"));
        }

        @Test
        @DisplayName("应返回封禁原因字符串")
        void shouldReturnBanReasonString() throws IOException {
            String script = loadScript("return_string.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("returnType", "ban");
            env.put("clientName", "FakeClient");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("FakeClient"));
        }
    }

    @Nested
    @DisplayName("客户端名称检查测试")
    class ClientNameCheckTests {

        @Test
        @DisplayName("应检测到黑名单客户端 - XunLei")
        void shouldDetectBlacklistedClientXunLei() throws IOException {
            String script = loadScript("client_name_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "XunLei/7.0");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("黑名单"));
        }

        @Test
        @DisplayName("应检测到黑名单客户端 - Thunder")
        void shouldDetectBlacklistedClientThunder() throws IOException {
            String script = loadScript("client_name_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "Thunder/5.0");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("黑名单"));
        }

        @Test
        @DisplayName("应允许正常客户端通过")
        void shouldAllowNormalClient() throws IOException {
            String script = loadScript("client_name_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }

        @Test
        @DisplayName("应处理空客户端名称")
        void shouldHandleBlankClientName() throws IOException {
            String script = loadScript("client_name_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "");

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }

        @Test
        @DisplayName("应处理 null 客户端名称")
        void shouldHandleNullClientName() throws IOException {
            String script = loadScript("client_name_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", null);

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("PeerId 验证测试")
    class PeerIdValidationTests {

        @Test
        @DisplayName("应检测到 qBittorrent 的 PeerId 伪装")
        void shouldDetectQBittorrentPeerIdSpoofing() throws IOException {
            String script = loadScript("peer_id_validation.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("peerId", "-TR2940-xxxxxxxxxxxx"); // 错误的 PeerId

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("伪装"));
        }

        @Test
        @DisplayName("应允许正确的 qBittorrent PeerId")
        void shouldAllowCorrectQBittorrentPeerId() throws IOException {
            String script = loadScript("peer_id_validation.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("peerId", "-qB4500-xxxxxxxxxxxx");

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }

        @Test
        @DisplayName("应检测到 Transmission 的 PeerId 伪装")
        void shouldDetectTransmissionPeerIdSpoofing() throws IOException {
            String script = loadScript("peer_id_validation.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "Transmission/3.0");
            env.put("peerId", "-qB4500-xxxxxxxxxxxx"); // 错误的 PeerId

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("伪装"));
        }

        @Test
        @DisplayName("应处理未知客户端")
        void shouldHandleUnknownClient() throws IOException {
            String script = loadScript("peer_id_validation.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "UnknownClient/1.0");
            env.put("peerId", "-XX0000-xxxxxxxxxxxx");

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("上传比例检查测试")
    class UploadRatioCheckTests {

        @Test
        @DisplayName("应检测到吸血行为 - 低上传比例")
        void shouldDetectLeechingBehavior() throws IOException {
            String script = loadScript("upload_ratio_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("downloaded", 1000000000L); // 1GB
            env.put("uploaded", 1000000L);      // 1MB
            env.put("minRatioThreshold", 0.1);
            env.put("minDownloadedThreshold", 100000000L); // 100MB

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("吸血"));
        }

        @Test
        @DisplayName("应允许正常的上传比例")
        void shouldAllowNormalUploadRatio() throws IOException {
            String script = loadScript("upload_ratio_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("downloaded", 1000000000L); // 1GB
            env.put("uploaded", 500000000L);    // 500MB
            env.put("minRatioThreshold", 0.1);
            env.put("minDownloadedThreshold", 100000000L);

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }

        @Test
        @DisplayName("应忽略低下载量的 Peer")
        void shouldIgnoreLowDownloadPeer() throws IOException {
            String script = loadScript("upload_ratio_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("downloaded", 1000000L); // 1MB - 低于阈值
            env.put("uploaded", 0L);
            env.put("minRatioThreshold", 0.1);
            env.put("minDownloadedThreshold", 100000000L); // 100MB

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("复杂条件检查测试")
    class ComplexConditionTests {

        @Test
        @DisplayName("应跳过客户端信息不完整的 Peer")
        void shouldSkipIncompleteClientInfo() throws IOException {
            String script = loadScript("complex_condition.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "");
            env.put("peerId", "");
            env.put("uploadSpeed", 0L);
            env.put("downloadSpeed", 0L);
            env.put("progress", 0.0);

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).startsWith("@"));
        }

        @Test
        @DisplayName("应检测到只下载不上传的行为")
        void shouldDetectDownloadOnlyBehavior() throws IOException {
            String script = loadScript("complex_condition.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "SomeClient");
            env.put("peerId", "-SC0001-");
            env.put("uploadSpeed", 0L);
            env.put("downloadSpeed", 2000000L); // 2MB/s
            env.put("progress", 0.5);

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("只下载不上传"));
        }

        @Test
        @DisplayName("应检测到进度100%仍在下载的异常行为")
        void shouldDetectDownloadingAt100Percent() throws IOException {
            String script = loadScript("complex_condition.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "SomeClient");
            env.put("peerId", "-SC0001-");
            env.put("uploadSpeed", 100000L);
            env.put("downloadSpeed", 100000L);
            env.put("progress", 1.0);

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("进度100%仍在下载"));
        }

        @Test
        @DisplayName("应检测到可疑客户端名称")
        void shouldDetectSuspiciousClientName() throws IOException {
            String script = loadScript("complex_condition.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "FakeClient");
            env.put("peerId", "-FC0001-");
            env.put("uploadSpeed", 100000L);
            env.put("downloadSpeed", 100000L);
            env.put("progress", 0.5);

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("可疑客户端名称"));
        }

        @Test
        @DisplayName("应允许正常的 Peer 通过")
        void shouldAllowNormalPeer() throws IOException {
            String script = loadScript("complex_condition.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("peerId", "-qB4500-");
            env.put("uploadSpeed", 100000L);
            env.put("downloadSpeed", 100000L);
            env.put("progress", 0.5);

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("IP 范围检查测试")
    class IpRangeCheckTests {

        @Test
        @DisplayName("应检测到特征 IPv6 段")
        void shouldDetectCharacteristicIpv6Segment() throws IOException {
            String script = loadScript("ip_range_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("ipAddress", "2001:db8:0:2e0:61ff:fe12:3456:7890");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("2e0:61ff:fe"));
        }

        @Test
        @DisplayName("应跳过私有地址 - 192.168.x.x")
        void shouldSkipPrivateAddress192() throws IOException {
            String script = loadScript("ip_range_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("ipAddress", "192.168.1.100");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).startsWith("@"));
        }

        @Test
        @DisplayName("应跳过私有地址 - 10.x.x.x")
        void shouldSkipPrivateAddress10() throws IOException {
            String script = loadScript("ip_range_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("ipAddress", "10.0.0.1");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).startsWith("@"));
        }

        @Test
        @DisplayName("应跳过回环地址")
        void shouldSkipLoopbackAddress() throws IOException {
            String script = loadScript("ip_range_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("ipAddress", "127.0.0.1");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).startsWith("@"));
        }

        @Test
        @DisplayName("应允许正常公网 IP 通过")
        void shouldAllowPublicIp() throws IOException {
            String script = loadScript("ip_range_check.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("ipAddress", "8.8.8.8");

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("正则表达式匹配测试")
    class RegexMatchTests {

        @Test
        @DisplayName("应检测到可疑的随机字符串客户端名称")
        void shouldDetectSuspiciousRandomClientName() throws IOException {
            String script = loadScript("regex_match.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "aB3dE5fG7hI9jK1lM3nO5pQ7"); // 24个随机字符
            env.put("peerId", "-qB4500-");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("随机客户端名称"));
        }

        @Test
        @DisplayName("应检测到非标准 PeerId 格式")
        void shouldDetectNonStandardPeerIdFormat() throws IOException {
            String script = loadScript("regex_match.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("peerId", "!@#$%^&*()12345678");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("非标准 PeerId"));
        }

        @Test
        @DisplayName("应允许正常的客户端和 PeerId")
        void shouldAllowNormalClientAndPeerId() throws IOException {
            String script = loadScript("regex_match.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("peerId", "-qB4500-xxxxxxxxxxxx");

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("列表操作测试")
    class ListOperationsTests {

        @Test
        @DisplayName("应检测到不在白名单中的客户端")
        void shouldDetectClientNotInWhitelist() throws IOException {
            String script = loadScript("list_operations.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "UnknownClient/1.0");
            env.put("downloadSpeed", 1000000L);
            env.put("uploadSpeed", 1000000L);

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("不在白名单中"));
        }

        @Test
        @DisplayName("应检测到异常高的下载速度")
        void shouldDetectAbnormallyHighDownloadSpeed() throws IOException {
            String script = loadScript("list_operations.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("downloadSpeed", 200 * 1024 * 1024L); // 200MB/s
            env.put("uploadSpeed", 1000000L);

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("下载速度异常高"));
        }

        @Test
        @DisplayName("应允许正常的白名单客户端和速度通过")
        void shouldAllowNormalWhitelistedClientAndSpeed() throws IOException {
            String script = loadScript("list_operations.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("downloadSpeed", 10 * 1024 * 1024L); // 10MB/s
            env.put("uploadSpeed", 5 * 1024 * 1024L);   // 5MB/s

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("字典操作测试")
    class DictOperationsTests {

        @Test
        @DisplayName("应检测到不信任的客户端 - XunLei")
        void shouldDetectUntrustedClientXunLei() throws IOException {
            String script = loadScript("dict_operations.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "XunLei/7.0");
            env.put("peerId", "-XL0070-xxxxxxxxxxxx");

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("不信任"));
        }

        @Test
        @DisplayName("应检测到 PeerId 不匹配")
        void shouldDetectPeerIdMismatch() throws IOException {
            String script = loadScript("dict_operations.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("peerId", "-TR2940-xxxxxxxxxxxx"); // 错误的 PeerId

            Object result = compiled.execute(env);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("PeerId 不匹配"));
        }

        @Test
        @DisplayName("应允许正确的信任客户端和 PeerId")
        void shouldAllowCorrectTrustedClientAndPeerId() throws IOException {
            String script = loadScript("dict_operations.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            Map<String, Object> env = compiled.newEnv();
            env.put("clientName", "qBittorrent/4.5.0");
            env.put("peerId", "-qB4500-xxxxxxxxxxxx");

            Object result = compiled.execute(env);
            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("无效语法脚本应编译失败返回 null")
        void invalidSyntaxScriptShouldReturnNull() throws IOException {
            String script = loadScript("invalid_syntax.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNull(compiled);
        }

        @Test
        @DisplayName("空脚本内容应编译失败返回 null")
        void nullScriptContentShouldReturnNull() {
            CompiledScript compiled = engine.compileScript(null, "test", null);

            assertNull(compiled);
        }
    }

    @Nested
    @DisplayName("线程安全测试")
    class ThreadSafetyTests {

        @Test
        @DisplayName("线程安全脚本应支持并发执行")
        void threadSafeScriptShouldSupportConcurrentExecution() throws Exception {
            String script = loadScript("return_numbers.py");
            CompiledScript compiled = engine.compileScript(null, "test", script);

            assertNotNull(compiled);
            assertTrue(compiled.threadSafe());

            // 创建多个线程并发执行
            Thread[] threads = new Thread[10];
            Object[] results = new Object[10];
            String[] actions = {"ban", "skip", "none", "ban", "skip", "none", "ban", "skip", "none", "ban"};

            for (int i = 0; i < 10; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    Map<String, Object> env = compiled.newEnv();
                    env.put("action", actions[index]);
                    results[index] = compiled.execute(env);
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // 验证结果
            assertEquals(1, results[0]); // ban
            assertEquals(2, results[1]); // skip
            assertEquals(0, results[2]); // none
        }
    }
}
