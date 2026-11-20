package com.ghostchu.peerbanhelper.util.scriptengine.lua;

import com.ghostchu.peerbanhelper.util.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngine;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public final class LuaJScriptEngine implements ScriptEngine {
    private static final Globals LIBRARIES = loadLibraries();
    private static final List<String> BASE_FUNCTIONS = List.of(
            "assert", "error", "ipairs", "next", "pairs", "rawequal", "rawget", "rawlen",
            "rawset", "select", "tonumber", "tostring", "type");

    private static Globals loadLibraries() {
        Globals libraries = new Globals();
        libraries.load(new BaseLib());
        // LuaJ's library initializers register their tables in package.loaded.
        LuaTable registry = new LuaTable();
        registry.set("loaded", new LuaTable());
        libraries.set("package", registry);
        libraries.load(new StringLib());
        libraries.load(new TableLib());
        libraries.load(new MathLib());
        libraries.get("math").set("random", LuaValue.NIL);
        libraries.get("math").set("randomseed", LuaValue.NIL);
        libraries.get("string").set("dump", LuaValue.NIL);
        return libraries;
    }

    private static LuaTable environment(Map<String, Object> env) {
        LuaTable globals = new LuaTable();
        globals.set("_G", globals);
        for (String name : BASE_FUNCTIONS) globals.set(name, LIBRARIES.get(name));
        for (String name : List.of("math", "string", "table")) {
            LuaTable original = LIBRARIES.get(name).checktable();
            LuaTable copy = new LuaTable();
            for (LuaValue key : original.keys()) copy.set(key, original.get(key));
            globals.set(name, copy);
        }
        env.forEach((key, value) -> globals.set(key, snapshot(value)));
        return globals;
    }

    private static LuaValue snapshot(Object value) {
        return switch (value) {
            case null -> LuaValue.NIL;
            case Boolean b -> LuaValue.valueOf(b);
            case String s -> LuaValue.valueOf(s);
            case Number n -> LuaValue.valueOf(n.doubleValue());
            case Map<?, ?> map -> {
                LuaTable table = new LuaTable(0, map.size());
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key))
                        throw new IllegalArgumentException("Expected a string snapshot key");
                    table.set(key, snapshot(entry.getValue()));
                }
                yield table;
            }
            case List<?> list -> {
                LuaTable table = new LuaTable(list.size(), 0);
                int index = 1;
                for (Object item : list) table.set(index++, snapshot(item));
                yield table;
            }
            default -> throw new IllegalArgumentException("Unsupported snapshot value: " + value.getClass().getName());
        };
    }

    @Override
    public String getEngineName() {
        return "LuaJ";
    }

    @Override
    public String getFileExtension() {
        return ".lua";
    }

    private static Prototype parse(String name, String scriptContent) {
        if (scriptContent == null) return null;
        var input = new ByteArrayInputStream(scriptContent.getBytes(StandardCharsets.UTF_8));
        try {
            return LuaC.instance.compile(input, name);
        } catch (IOException | LuaError e) {
            return null;
        }
    }

    @Override
    public boolean validateScriptSyntax(String fallbackName, String scriptContent) {
        return parse(fallbackName == null ? "rule.lua" : fallbackName, scriptContent) != null;
    }

    @Override
    public CompiledScript compileScript(File file, String fallbackName, String scriptContent) {
        String name = fallbackName != null ? fallbackName : file != null ? file.getName() : "rule.lua";
        Prototype prototype = parse(name, scriptContent);
        return prototype == null ? null : new Rule(file, name, scriptContent, prototype);
    }

    private record Rule(File file, String name, String script, Prototype prototype) implements CompiledScript {
        @Override
        public Object execute(Map<String, Object> env) {
            LuaValue result = new LuaClosure(prototype, environment(env)).call();
            return switch (result.type()) {
                case LuaValue.TNIL -> null;
                case LuaValue.TBOOLEAN -> result.toboolean();
                case LuaValue.TSTRING -> result.tojstring();
                case LuaValue.TNUMBER -> {
                    if (result.isint()) yield result.toint();
                    if (!Double.isFinite(result.todouble()))
                        throw new IllegalArgumentException("Expected a finite Lua rule result");
                    yield result.todouble();
                }
                default -> throw new IllegalArgumentException("Expected a scalar Lua rule result");
            };
        }

        @Override public Map<String, Object> newEnv() { return new HashMap<>(); }
        @Override public int scriptHashCode() { return prototype.hashCode(); }
        @Override public String author() { return "Unknown"; }
        @Override public String version() { return "null"; }
        @Override public boolean cacheable() { return false; }
        @Override public boolean threadSafe() { return true; }
    }
}
