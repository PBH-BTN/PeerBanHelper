package com.ghostchu.peerbanhelper.config;

import com.google.common.io.CharStreams;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public final class PBHConfigUpdater {
    private static final String CONFIG_VERSION_KEY = "config-version";
    private final YamlConfiguration yaml;
    private final YamlConfiguration bundle;
    private final File file;

    public PBHConfigUpdater(File file, YamlConfiguration yaml, InputStream resourceAsStream) {
        this.yaml = yaml;
        this.file = file;
        this.bundle = new YamlConfiguration();
        try (resourceAsStream; InputStreamReader reader = new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8)) {
            this.bundle.loadFromString(CharStreams.toString(reader));
        } catch (IOException | InvalidConfigurationException e) {
            log.error("Unable to load the bundled config from classloader resource", e);
        }
    }

    public void update(@NotNull Object configUpdateScript) {
        log.info("Checking configuration...");
        int selectedVersion = yaml.getInt(CONFIG_VERSION_KEY, -1);
        String oldContent = yaml.saveToString();
        for (Method method : getUpdateScripts(configUpdateScript)) {
            try {
                UpdateScript updateScript = method.getAnnotation(UpdateScript.class);
                int current = yaml.getInt(CONFIG_VERSION_KEY);
                if (current >= updateScript.version()) {
                    continue;
                }
                log.info("Upgrading configuration from {} to {}...", current, updateScript.version());
                String scriptName = updateScript.description();
                if (scriptName == null || scriptName.isEmpty()) {
                    scriptName = method.getName();
                }
                log.info("Executing upgrade script: {}", scriptName);
                try {
                    if (method.getParameterCount() == 0) {
                        method.invoke(configUpdateScript);
                    } else {
                        if (method.getParameterCount() == 1 && (method.getParameterTypes()[0] == YamlConfiguration.class)) {
                            method.invoke(configUpdateScript, bundle);
                        }
                    }
                } catch (Exception e) {
                    log.info("Error while executing upgrade script: method={}, target_ver={}", method.getName(), updateScript.version(), e);
                }
                yaml.set(CONFIG_VERSION_KEY, updateScript.version());
                log.info("Configuration successfully updated");
            } catch (Throwable throwable) {
                log.error("Error while updating configuration, method={}, target_ver={}", method.getName(), method.getAnnotation(UpdateScript.class).version(), throwable);
            }
        }
        log.info("Saving configuration changes...");
        try {
            migrateComments(yaml, bundle);
            String newContent = yaml.saveToString();
            if (!newContent.equals(oldContent)) {
                yaml.save(file);
            }
        } catch (IOException e) {
            log.error("Failed to save configuration!", e);
        }
    }

    private void migrateComments(YamlConfiguration yaml, YamlConfiguration bundle) {
        for (String key : yaml.getKeys(true)) {
            var inlineBundled = bundle.getInlineComments(key);
            var inlineYaml = yaml.getInlineComments(key);
            var stdBundled = bundle.getComments(key);
            var stdYaml = yaml.getComments(key);
            if (inlineYaml.isEmpty() && !inlineBundled.isEmpty()) {
                yaml.setInlineComments(key, inlineBundled);
            }
            if (stdYaml.isEmpty() && !stdBundled.isEmpty()) {
                yaml.setComments(key, stdBundled);
            }
        }
    }

    @NotNull
    public List<Method> getUpdateScripts(@NotNull Object configUpdateScript) {
        List<Method> methods = new ArrayList<>();
        for (Method declaredMethod : configUpdateScript.getClass().getDeclaredMethods()) {
            if (declaredMethod.getAnnotation(UpdateScript.class) == null) {
                continue;
            }
            methods.add(declaredMethod);
        }
        methods.sort(Comparator.comparingInt(o -> o.getAnnotation(UpdateScript.class).version()));
        return methods;
    }


}
