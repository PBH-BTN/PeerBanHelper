package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.text.Lang;
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
public class PBHConfigUpdater {
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
        log.info(Lang.CONFIG_CHECKING);
        int selectedVersion = yaml.getInt(CONFIG_VERSION_KEY, -1);
        for (Method method : getUpdateScripts(configUpdateScript)) {
            try {
                UpdateScript updateScript = method.getAnnotation(UpdateScript.class);
                int current = yaml.getInt(CONFIG_VERSION_KEY);
                if (current >= updateScript.version()) {
                    continue;
                }
                log.info(Lang.CONFIG_MIGRATING, current, updateScript.version());
                String scriptName = updateScript.description();
                if (scriptName == null || scriptName.isEmpty()) {
                    scriptName = method.getName();
                }
                log.info(Lang.CONFIG_EXECUTE_MIGRATE, scriptName);
                try {
                    if (method.getParameterCount() == 0) {
                        method.invoke(configUpdateScript);
                    } else {
                        if (method.getParameterCount() == 1 && (method.getParameterTypes()[0] == int.class || method.getParameterTypes()[0] == Integer.class)) {
                            method.invoke(configUpdateScript, current);
                        }
                    }
                } catch (Exception e) {
                    log.info(Lang.CONFIG_MIGRATE_FAILED, method.getName(), updateScript.version(), e.getMessage(), e);
                }
                yaml.set(CONFIG_VERSION_KEY, updateScript.version());
                log.info(Lang.CONFIG_UPGRADED, updateScript.version());
            } catch (Throwable throwable) {
                log.info(Lang.CONFIG_MIGRATE_FAILED, method.getName(), method.getAnnotation(UpdateScript.class).version(), throwable);
            }
        }
        log.info(Lang.CONFIG_SAVE_CHANGES);
        try {
            migrateComments(yaml, bundle);
            yaml.save(file);
        } catch (IOException e) {
            log.error(Lang.CONFIG_SAVE_ERROR, e);
        }
    }

    private void migrateComments(YamlConfiguration yaml, YamlConfiguration bundle) {
        for (String key : yaml.getKeys(true)) {
            var inlineBundled = bundle.getInLineComments(key);
            var inlineYaml = yaml.getInLineComments(key);
            var stdBundled = bundle.getComments(key);
            var stdYaml = yaml.getComments(key);
            if (inlineYaml.isEmpty()) {
                yaml.setInLineComments(key, inlineBundled);
            }
            if (stdYaml.isEmpty()) {
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
