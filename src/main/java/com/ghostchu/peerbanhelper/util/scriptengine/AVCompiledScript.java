package com.ghostchu.peerbanhelper.util.scriptengine;

import com.googlecode.aviator.Expression;

import java.io.File;

public record AVCompiledScript(File file, String name, String author, boolean cacheable, boolean threadSafe,
                               String version, String script, Expression expression) implements CompiledScript {

    @Override
    public Object execute(java.util.Map<String, Object> env) {
        if (threadSafe()) {
            return expression.execute(env);
        } else {
            synchronized (expression) {
                return expression.execute(env);
            }
        }
    }

    @Override
    public java.util.Map<String, Object> newEnv() {
        return expression.newEnv();
    }

    @Override
    public int scriptHashCode() {
        return expression.hashCode();
    }
}