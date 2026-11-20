package com.ghostchu.peerbanhelper.util.scriptengine.javascript;

import com.ghostchu.peerbanhelper.util.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngine;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public final class GraalJsScriptEngine implements ScriptEngine {
    private static final System.Logger LOG = System.getLogger(GraalJsScriptEngine.class.getName());

    private static final class Runtime {
        private static final Engine ENGINE = Engine.newBuilder("js").useSystemProperties(false)
                .option("engine.WarnInterpreterOnly", "false").build();
        private static final Source NEW_MAP = Source.create("js", "() => Object.create(null)");
        private static final Source NEW_LIST = Source.create("js", "() => []");
    }

    private static Context openContext() {
        return Context.newBuilder("js").engine(Runtime.ENGINE)
                .allowHostAccess(HostAccess.NONE).allowHostClassLookup(null).allowIO(IOAccess.NONE)
                .option("js.load", "false").option("js.print", "false").option("js.console", "false")
                .in(InputStream.nullInputStream()).out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream()).build();
    }

    @Override
    public String getEngineName() {
        return "GraalJS";
    }

    @Override
    public String getFileExtension() {
        return ".js";
    }

    @Override
    public CompiledScript compileScript(File file, String fallbackName, String scriptContent) {
        String name = fallbackName != null ? fallbackName : file != null ? file.getName() : "rule.js";
        Source source = parse(name, scriptContent, true);
        return source == null ? null : new Rule(file, name, scriptContent, source);
    }

    @Override
    public boolean validateScriptSyntax(String fallbackName, String scriptContent) {
        return parse(fallbackName == null ? "rule.js" : fallbackName, scriptContent, false) != null;
    }

    private static Source parse(String name, String scriptContent, boolean cache) {
        if (scriptContent == null) return null;
        try (Context context = openContext()) {
            Source source = Source.newBuilder("js", scriptContent, name).mimeType("application/javascript")
                    .cached(cache).buildLiteral();
            context.parse(source);
            return source;
        } catch (RuntimeException | LinkageError e) {
            if (!(e instanceof PolyglotException guest && guest.isSyntaxError())) {
                LOG.log(System.Logger.Level.WARNING, "Unable to prepare GraalJS rule: " + name, e);
            }
            return null;
        }
    }

    private record Snapshot(Value newMap, Value newList) {
        Object copy(Object value) {
            return switch (value) {
                case null -> null;
                case Boolean b -> b;
                case String s -> s;
                case Number n -> n.doubleValue();
                case Map<?, ?> map -> {
                    Value object = newMap.execute();
                    for (var entry : map.entrySet()) {
                        if (!(entry.getKey() instanceof String key))
                            throw new IllegalArgumentException("Expected a string snapshot key");
                        object.putMember(key, copy(entry.getValue()));
                    }
                    yield object;
                }
                case List<?> list -> {
                    Value array = newList.execute();
                    int index = 0;
                    for (Object item : list) array.setArrayElement(index++, copy(item));
                    yield array;
                }
                default -> throw new IllegalArgumentException("Unsupported snapshot value: " + value.getClass().getName());
            };
        }
    }

    private record Rule(File file, String name, String script, Source source) implements CompiledScript {
        @Override
        public Object execute(Map<String, Object> env) {
            try (Context context = openContext()) {
                Snapshot snapshot = new Snapshot(context.eval(Runtime.NEW_MAP), context.eval(Runtime.NEW_LIST));
                Value bindings = context.getBindings("js");
                for (var entry : env.entrySet()) {
                    bindings.putMember(entry.getKey(), snapshot.copy(entry.getValue()));
                }
                Value result = context.eval(source);
                if (result.isNull()) return null;
                if (result.isBoolean()) return result.asBoolean();
                if (result.isString()) return result.asString();
                if (result.fitsInInt()) return result.asInt();
                if (result.fitsInDouble() && Double.isFinite(result.asDouble())) return result.asDouble();
                throw new IllegalArgumentException("Expected a scalar JavaScript rule result");
            }
        }

        @Override public Map<String, Object> newEnv() { return new HashMap<>(); }
        @Override public int scriptHashCode() { return source.hashCode(); }
        @Override public String author() { return "Unknown"; }
        @Override public String version() { return "null"; }
        @Override public boolean cacheable() { return false; }
        @Override public boolean threadSafe() { return true; }
    }
}
