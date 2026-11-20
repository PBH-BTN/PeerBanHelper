package com.ghostchu.peerbanhelper.util.scriptengine.lua;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class LuaJScriptEngineTest {
    private final LuaJScriptEngine engine = new LuaJScriptEngine();

    @Test
    void checksSyntaxWithoutRunningTheRule() {
        assertEquals(".lua", engine.getFileExtension());
        assertTrue(engine.validateScriptSyntax("rule.lua", "error('runtime')"));
        assertNotNull(engine.compileScript(null, "rule.lua", "error('runtime')"));
        assertFalse(engine.validateScriptSyntax(null, "local ="));
        assertNull(engine.compileScript(null, null, "local ="));
    }

    @Test
    void reusesCompiledRuleWithIndependentSnapshots() throws Exception {
        var rule = engine.compileScript(null, "rule.lua", "seen = (seen or 0) + 1\npeer.tags[1] = peer.tags[1] .. '!'\nreturn peer.tags[1] .. seen");
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
                "return peer.flags == nil and peer.port == 6881 and peer.active and #peer.tags == 0");
        assertEquals(true, rule.execute(Map.of("peer", peer)));
    }

    @Test
    void exposesOnlyRuleDataAndScalarResults() {
        var rule = engine.compileScript(null, null, "return os == nil and io == nil and package == nil and require == nil and luajava == nil and loadfile == nil and debug == nil and getmetatable == nil");
        assertNotNull(rule);
        assertEquals(true, rule.execute(rule.newEnv()));
        assertThrows(IllegalArgumentException.class, () -> rule.execute(Map.of("nested", List.of(new Object()))));
        assertThrows(IllegalArgumentException.class,
                () -> engine.compileScript(null, null, "return math.huge").execute(Map.of()));
        assertEquals("2", engine.compileScript(null, null, "return '2'").execute(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> engine.compileScript(null, null, "return {}").execute(Map.of()));
    }
}
