package com.ghostchu.peerbanhelper.util.scriptengine;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxPool;
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Init;
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Metadata;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScriptEngineManagerTest {
    @Test
    void btnAlwaysUsesSandboxAndOnlyExtensionlessBtnNamesDefaultToAviator() {
        var engine = mock(ScriptEngine.class);
        when(engine.getFileExtension()).thenReturn(".av");
        var manager = new ScriptEngineManager(List.of(engine));
        assertSame(engine, manager.getEngineByFileName("local.AV"));
        assertNull(manager.getEngineByFileName("local"));
        String source = "return false;";
        var file = new File("local.AV");
        var trusted = mock(CompiledScript.class);
        when(engine.compileScript(file, file.getName(), source)).thenReturn(trusted);
        try (var main = mockStatic(Main.class); var pool = mockStatic(SandboxPool.class)) {
            assertSame(trusted, manager.compileLocal(file, source, false));
            var remote = new Init(".av", "legacy", source, "btn");
            pool.when(() -> SandboxPool.prepare(remote)).thenReturn(new Metadata("remote rule", "Author", "1", true, true));
            assertEquals("remote rule", manager.compileBtn("legacy", source).name());
            assertThrows(IllegalArgumentException.class, () -> manager.compileBtn("bad.txt", source));
            var local = new Init(".av", file.getName(), source, "local");
            pool.when(() -> SandboxPool.prepare(local)).thenThrow(new IOException("unavailable"));
            assertThrows(IOException.class, () -> manager.compileLocal(file, source, true));
            verify(engine, times(1)).compileScript(any(), any(), any());
        }
    }
}
