package com.ghostchu.peerbanhelper.util.scriptengine.aviator;

import com.ghostchu.peerbanhelper.util.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngine;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.EvalMode;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.math.MathContext;

@Component
@Slf4j
public final class AVScriptEngine implements ScriptEngine {

    private final AviatorEvaluatorInstance evaluator;
    private final boolean sandboxed;

    public AVScriptEngine() {
        this(false);
    }

    /** The sandboxed evaluator is for a worker already isolated by the host OS. */
    public AVScriptEngine(boolean sandboxed) {
        this.sandboxed = sandboxed;
        evaluator = AviatorEvaluator.newInstance(EvalMode.ASM);
        evaluator.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        evaluator.setOption(Options.MATH_CONTEXT, MathContext.DECIMAL32);
        evaluator.setOption(Options.ENABLE_PROPERTY_SYNTAX_SUGAR, true);
        evaluator.setCachedExpressionByDefault(!sandboxed);
        if (sandboxed) {
            SandboxedAviator.configure(evaluator);
        } else {
            TrustedAviator.configure(evaluator);
        }
    }

    @Override
    @Nullable
    public CompiledScript compileScript(File file, String fallbackName, String scriptContent) {
        try (BufferedReader reader = new BufferedReader(new StringReader(scriptContent))) {
            String name = fallbackName;
            String author = "Unknown";
            String version = "null";
            boolean cacheable = true;
            boolean threadSafe = true;
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.startsWith("##")) {
                    line = line.substring(2).trim();
                    if (line.startsWith("@NAME")) {
                        name = line.substring(5).trim();
                    } else if (line.startsWith("@AUTHOR")) {
                        author = line.substring(7).trim();
                    } else if (line.startsWith("@CACHEABLE")) {
                        cacheable = Boolean.parseBoolean(line.substring(10).trim());
                    } else if (line.startsWith("@VERSION")) {
                        version = line.substring(8).trim();
                    } else if (line.startsWith("@THREADSAFE")) {
                        threadSafe = Boolean.parseBoolean(line.substring(11).trim());
                    }
                }
            }
            Expression expression = evaluator.compile(scriptContent, !sandboxed);
            return new AVCompiledScript(
                    file,
                    name,
                    author,
                    cacheable,
                    threadSafe,
                    version,
                    scriptContent,
                    expression
            );
        } catch (Exception e) {
            log.warn("Script Engine unable to compile the script: {}", fallbackName, e);
            return null;
        }
    }

    @Override
    public boolean validateScriptSyntax(String fallbackName, String scriptContent) {
        try {
            evaluator.validate(scriptContent);
            return true;
        } catch (Exception e) {
            log.warn("Script Engine unable to validate the script: {}", fallbackName, e);
            return false;
        }
    }

    @Override
    public String getEngineName() {
        return "Aviator";
    }

    @Override
    public String getFileExtension() {
        return ".av";
    }
}
