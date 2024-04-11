package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.impl.*;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ModuleManager {

    public static final String PLUGIN_CLASS_NAME = "com.ghostchu.peerbanhelper.module.Plugin";

    private final PeerBanHelperServer server;
    private final YamlConfiguration profile;

    @Getter
    private List<FeatureModule> registeredModules = new ArrayList<>();

    private final Map<Class<?>, Object> dynamicModules = new HashMap<>();

    public ModuleManager(PeerBanHelperServer server, YamlConfiguration profile) {
        this.server = server;
        this.profile = profile;
    }

    public void registerModules() {
        log.info(Lang.WAIT_FOR_MODULES_STARTUP);
        this.registeredModules.clear();
        List<FeatureModule> modules = new ArrayList<>();
        modules.add(new IPBlackList(profile));
        modules.add(new PeerIdBlacklist(profile));
        modules.add(new ClientNameBlacklist(profile));
        modules.add(new ProgressCheatBlocker(profile));
        modules.add(new ActiveProbing(profile));
        modules.add(new AutoRangeBan(server, profile));
        this.registeredModules.addAll(modules.stream().filter(FeatureModule::isModuleEnabled).toList());
        // load embed plugin
        this.registeredModules.forEach(FeatureModule::register);

        // load external plugin
        this.loadPlugin();
    }

    public void unregisterModules() {
        //todo place some clean code here
        dynamicModules.forEach((clazz, obj) -> {
            try {
                clazz.getMethod("stop").invoke(obj);
            } catch (Exception e) {
                log.error("Failed to stop plugin", e);
            }
        });

        this.registeredModules.forEach(FeatureModule::stop);
    }

    private void loadPlugin() {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            log.info("Native image, skip");
            return;
        }
        try {
            // list file in the plugin folder
            var plugins = new File("data/plugins").listFiles();
            if (plugins != null) {
                for (File plugin : plugins) {
                    if (plugin.getName().endsWith(".jar")) {
                        var loader = new URLClassLoader(new URL[]{plugin.toURI().toURL()});
                        var clazz = loader.loadClass(PLUGIN_CLASS_NAME);
                        var instance = clazz.getDeclaredConstructor().newInstance();
                        clazz.getMethod("register").invoke(instance);
                        dynamicModules.put(clazz, instance);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load plugin", e);
        }
    }

}
