package com.ghostchu.peerbanhelper.util.scriptengine.javascript;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class GraalJsScriptEngineTest {
    private final GraalJsScriptEngine engine = new GraalJsScriptEngine();

    @Test
    void checksSyntaxWithoutRunningTheRule() {
        assertEquals(".js", engine.getFileExtension());
        assertTrue(engine.validateScriptSyntax("rule.js", "throw new Error('runtime');"));
        assertNotNull(engine.compileScript(null, "rule.js", "throw new Error('runtime');"));
        assertFalse(engine.validateScriptSyntax(null, "const = ;"));
        assertFalse(engine.validateScriptSyntax(null, "return true;"));
        var function = engine.compileScript(null, null, "function check() { return 2; } check();");
        assertEquals(2, function.execute(Map.of()));
        assertNull(engine.compileScript(null, null, "const = ;"));
    }

    @Test
    void reusesCompiledRuleWithIndependentSnapshots() throws Exception {
        var rule = engine.compileScript(null, "rule.js", "globalThis.seen = (globalThis.seen || 0) + 1;\npeer.tags[0] += '!';\npeer.tags[0] + seen");
        assertNotNull(rule);
        var peer = Map.of("tags", List.of("甲"));
        try (var tasks = Executors.newFixedThreadPool(2)) {
            var first = tasks.submit(() -> rule.execute(Map.of("peer", peer)));
            var second = tasks.submit(() -> rule.execute(Map.of("peer", Map.of("tags", List.of("乙")))));
            assertEquals("甲!1", first.get());
            assertEquals("乙!1", second.get());
        }
        assertEquals("甲!1", rule.execute(Map.of("peer", peer)));
        assertEquals(List.of("甲"), peer.get("tags"));
    }

    @Test
    void readsNullableAndNumericSnapshotFields() {
        var peer = new HashMap<String, Object>();
        peer.put("flags", null);
        peer.put("port", 6881L);
        peer.put("active", true);
        peer.put("tags", List.of());
        var rule = engine.compileScript(null, null,
                "peer.flags === null && peer.port === 6881 && peer.active && peer.tags.length === 0");
        assertEquals(true, rule.execute(Map.of("peer", peer)));
        var equality = engine.compileScript(null, null, "a === b");
        assertEquals(true, equality.execute(Map.of("a", 1L << 53, "b", (1L << 53) + 1)));
    }

    @Test
    void exposesOnlyRuleDataAndScalarResults() {
        var rule = engine.compileScript(null, null, "[typeof Java, typeof load, typeof require, typeof print].every(x => x === 'undefined')");
        assertNotNull(rule);
        assertEquals(true, rule.execute(rule.newEnv()));
        assertThrows(IllegalArgumentException.class, () -> rule.execute(Map.of("nested", List.of(new Object()))));
        assertThrows(IllegalArgumentException.class,
                () -> engine.compileScript(null, null, "Infinity").execute(Map.of()));
        assertEquals("2", engine.compileScript(null, null, "'2'").execute(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> engine.compileScript(null, null, "({})").execute(Map.of()));
    }
}
