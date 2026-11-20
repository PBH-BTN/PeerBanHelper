package com.ghostchu.peerbanhelper.util.scriptengine.aviator;

import com.ghostchu.peerbanhelper.util.scriptengine.CompiledScript;
import com.googlecode.aviator.exception.FunctionNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AVScriptEngineTest {
    private static String resource(String path) throws IOException {
        try (var stream = Objects.requireNonNull(AVScriptEngineTest.class.getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static CompiledScript compile(AVScriptEngine engine, String name, String source) {
        var script = engine.compileScript(null, name, source);
        assertNotNull(script, name);
        return script;
    }

    private static Map<String, Object> peer(String client, String id, String address) {
        return Map.of("clientName", client, "peerId", id, "peerAddress", Map.of("address", address));
    }

    static Stream<Arguments> shippedScripts() {
        return Stream.of(
                Arguments.of("2e0-61ff-fe.av", peer("example", "-EX", "2001:db8:2e0:61ff:fe00::1"),
                        "2e0:61ff:fe 特征段随机后缀 IPv6 识别"),
                Arguments.of("dot-1-ipv6-tr296.av", peer("Transmission 2.94", "-TR", "2001:db8::1"),
                        "Transmission 2.94 (IPV6 ::1) 多拨伪装吸血"),
                Arguments.of("gopeed-random-peerid.av", peer("Gopeed Dev", "-XX", "192.0.2.1"),
                        "Gopeed Dev 全随机 PeerID 检测"),
                Arguments.of("name-id-verify.av", peer("qBittorrent", "-XX", "192.0.2.1"),
                        "Peer reporting: PeerId=-xx, ClientName=qbittorrent, But PBH excepted=-qb"),
                Arguments.of("peer-ids.av.example", peer("example", "-HP", "192.0.2.1"), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shippedScripts")
    void shippedScriptRunsOnWorkerSnapshot(String file, Map<String, Object> matchingPeer, Object expected)
            throws IOException {
        var script = compile(new AVScriptEngine(true), file, resource("/scripts/" + file));
        var env = script.newEnv();
        env.put("peer", matchingPeer);
        assertEquals(expected, script.execute(env));

        env = script.newEnv();
        env.put("peer", peer("qBittorrent", "-QB", "192.0.2.1"));
        assertEquals(false, script.execute(env));
    }

    @Test
    void sandboxKeepsScriptMetadataAndRejectsInvalidSyntax() throws IOException {
        var engine = new AVScriptEngine(true);
        assertEquals(".av", engine.getFileExtension());
        String source = resource("/testdata/scripts/aviator/complex_condition.av");
        assertTrue(engine.validateScriptSyntax("fixture", source));
        var script = compile(engine, "fixture", source);
        assertEquals("复杂条件检查", script.name());
        assertEquals("Test", script.author());
        assertEquals("1.0", script.version());
        assertFalse(script.cacheable());
        assertFalse(script.threadSafe());
        assertEquals(source, script.script());

        var invalid = resource("/testdata/scripts/aviator/invalid_syntax.av");
        assertFalse(engine.validateScriptSyntax("invalid", invalid));
        assertNull(engine.compileScript(null, "invalid", invalid));
    }

    @Test
    void onlyTrustedEvaluatorHasLocalHelpersAndMethodLookup() {
        var trusted = new AVScriptEngine();
        var worker = new AVScriptEngine(true);
        for (String source : new String[]{"StrUtil.toUpperCaseRoot('local')", "toUpperCase('local')"}) {
            var localScript = compile(trusted, "trusted", source);
            assertEquals("LOCAL", localScript.execute(localScript.newEnv()));
            var workerScript = compile(worker, "worker", source);
            assertThrows(FunctionNotFoundException.class, () -> workerScript.execute(workerScript.newEnv()));
            assertEquals("LOCAL", localScript.execute(localScript.newEnv()));
        }
    }
}
