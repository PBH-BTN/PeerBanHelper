package com.ghostchu.peerbanhelper.scriptengine;

import com.googlecode.aviator.Expression;

import java.io.File;

public record CompiledScript(File file, String name, String author, boolean cacheable, boolean threadSafe,
                             String version, String script, Expression expression) {

}
