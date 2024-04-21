package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class PBHConfigUpdater {
    private static final String CONFIG_VERSION_KEY = "config-version";
    private final YamlConfiguration yaml;
    private final File file;

    public PBHConfigUpdater(File file, YamlConfiguration yaml) {
        this.yaml = yaml;
        this.file = file;
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
            yaml.save(file);
        } catch (IOException e) {
            log.warn(Lang.CONFIG_SAVE_ERROR, e);
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
