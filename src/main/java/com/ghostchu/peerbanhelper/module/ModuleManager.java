package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.config.ConfigManager;
import com.ghostchu.peerbanhelper.module.impl.*;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ModuleManager {

    public static final String PLUGIN_CLASS_NAME = "com.ghostchu.peerbanhelper.module.Plugin";

    private final PeerBanHelperServer server;

    @Getter
    private List<FeatureModule> registeredModules = new ArrayList<>();

    private final Map<Class<?>, Object> dynamicModules = new HashMap<>();

    public ModuleManager(PeerBanHelperServer server) {
        this.server = server;
    }

    public void registerModules() {
        log.info(Lang.WAIT_FOR_MODULES_STARTUP);
        this.registeredModules.clear();
        List<FeatureModule> modules = new ArrayList<>();
        modules.add(new PeerIdBlacklist(ConfigManager.Sections.modulePeerIdBlacklist()));
        modules.add(new ClientNameBlacklist(ConfigManager.Sections.moduleClientNameBlacklist()));
        modules.add(new IPBlackList(ConfigManager.Sections.moduleIPBlacklist()));
        modules.add(new ProgressCheatBlocker(ConfigManager.Sections.moduleProgressCheatBlocker()));
        modules.add(new ActiveProbing(ConfigManager.Sections.moduleActiveProbing()));
        modules.add(new AutoRangeBan(server, ConfigManager.Sections.moduleAutoRangeBan()));
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

    public void registerModule(FeatureModule module) {
        if (!module.isModuleEnabled())
            return;
        module.register();
        this.registeredModules.add(module);
    }

    public void unregisterModule(FeatureModule module) {
        if (!this.registeredModules.contains(module))
            return;
        module.stop();
        this.registeredModules.remove(module);
    }

    public Set<FeatureModule> getModulesByClass(Class<? extends FeatureModule> clazz) {
        return registeredModules.stream()
                .filter(module -> module.getClass() == clazz)
                .collect(Collectors.toSet());
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
