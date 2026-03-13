package com.ghostchu.peerbanhelper.util.scriptengine;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class AVScriptEngine implements ScriptEngine {

    @Override
    @Nullable
    public CompiledScript compileScript(File file, String fallbackName, String scriptContent) {
        var platform = Main.getPlatform();
        if (platform != null) {
            try (var scanner = platform.getMalwareScanner()) {
                if (scanner != null) {
                    if (scanner.isMalicious(scriptContent)) {
                        log.error(tlUI(Lang.MALWARE_SCANNER_DETECTED, "UserScript", file != null ? file.getAbsolutePath() : "<memory script>"));
                        return null;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
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
                if (line.startsWith("#")) {
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
            AviatorEvaluator.getInstance().validate(scriptContent);
            Expression expression = AviatorEvaluator.getInstance().compile(scriptContent, true);
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
    public String getEngineName() {
        return "Aviator";
    }

    @Override
    public String getFileExtension() {
        return ".av";
    }
}
