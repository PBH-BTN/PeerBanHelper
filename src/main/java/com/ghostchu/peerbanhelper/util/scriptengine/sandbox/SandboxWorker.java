package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngine;
import com.ghostchu.peerbanhelper.util.scriptengine.aviator.AVScriptEngine;
import com.ghostchu.peerbanhelper.util.scriptengine.javascript.GraalJsScriptEngine;
import com.ghostchu.peerbanhelper.util.scriptengine.lua.LuaJScriptEngine;

import java.io.*;
import java.util.Map;

public final class SandboxWorker {
    private SandboxWorker() {}

    public static void main(String[] args) throws IOException {
        var input = new BufferedInputStream(System.in);
        var output = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out));
        System.setIn(InputStream.nullInputStream());
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        serve(input, output);
    }

    static void serve(InputStream input, OutputStream output) throws IOException {
        var init = SandboxProtocol.read(input, SandboxProtocol.Init.class);
        ScriptEngine engine = switch (init.extension()) {
            case ".av" -> new AVScriptEngine(true);
            case ".js" -> new GraalJsScriptEngine();
            case ".lua" -> new LuaJScriptEngine();
            default -> throw new IOException("Unknown script engine");
        };
        var script = engine.compileScript(null, init.filename(), init.source());
        if (script == null) {
            SandboxProtocol.write(output, new SandboxProtocol.Metadata(null, null, null, false, false));
            return;
        }
        SandboxProtocol.write(output, new SandboxProtocol.Metadata(script.name(), script.author(), script.version(),
                script.cacheable(), script.threadSafe()));
        while (true) {
            Map<?, ?> snapshot;
            try {
                snapshot = SandboxProtocol.read(input, Map.class);
            } catch (EOFException closed) {
                return;
            }
            var env = script.newEnv();
            snapshot.forEach((key, value) -> env.put((String) key, value));
            SandboxProtocol.Reply reply;
            try {
                Object result = script.execute(env);
                if (!SandboxProtocol.isScalar(result)) throw new IllegalArgumentException("Expected a scalar result");
                reply = new SandboxProtocol.Reply(result, null);
            } catch (RuntimeException ex) {
                reply = new SandboxProtocol.Reply(null, ex.getClass().getSimpleName());
            }
            SandboxProtocol.write(output, reply);
        }
    }
}
