package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.Main;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public final class ModuleManager {
    private final List<FeatureModule> modules = new ArrayList<>();

    /**
     * 注册一个功能模块并启用它
     *
     * @param moduleClass 功能模块
     */
    public void register(@NotNull Class<? extends FeatureModule> moduleClass) {
        Main.registerBean(moduleClass, null);
        FeatureModule module = Main.getApplicationContext().getBean(moduleClass);
        if (module.isModuleEnabled()) {
            synchronized (modules) {
                this.modules.add(module);
                module.enable();
            }
        }
    }

    /**
     * 注册一个功能模块并启用它
     *
     * @param module 功能模块
     */
    public void register(@NotNull FeatureModule module, String beanName) {
        Main.registerBean(module.getClass(), beanName);
        if (module.isModuleEnabled()) {
            synchronized (modules) {
                this.modules.add(module);
                module.enable();
            }
        }
    }

    /**
     * 解注册一个功能模块、禁用它并清理注册的资源
     *
     * @param module 功能模块
     * @return 该功能模块成功解注册
     */
    public boolean unregister(@NotNull FeatureModule module) {
        synchronized (modules) {
            module.disable();
            return this.modules.remove(module);
        }
    }

    /**
     * 解注册给定的 Class 的所有功能模块实例
     *
     * @param module 功能模块类
     */
    public void unregister(@NotNull Class<FeatureModule> module) {
        synchronized (modules) {
            List<FeatureModule> moduleList = new ArrayList<>();
            for (FeatureModule featureModule : modules) {
                if (featureModule.getClass().equals(module)) {
                    moduleList.add(featureModule);
                }
            }
            moduleList.forEach(this::unregister);
        }
    }

    /**
     * 解注册所有的功能模块
     */
    public void unregisterAll() {
        List.copyOf(this.modules).forEach(this::unregister);
    }

    /**
     * 获取注册的所有功能模块
     *
     * @return 功能模块列表（不可修改）
     */
    @NotNull
    public List<FeatureModule> getModules() {
        return List.copyOf(modules);
    }

    /**
     * 从插件目录加载 JVM 插件
     */
    public void loadPlugin() {
//        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
//            log.info(Lang.SKIP_LOAD_PLUGIN_FOR_NATIVE_IMAGE);
//            return;
//        }
//        try {
//            // list file in the plugin folder
//            var plugins = new File("data/plugins").listFiles();
//            if (plugins != null) {
//                for (File plugin : plugins) {
//                    if (plugin.getName().endsWith(".jar")) {
//                        var loader = new URLClassLoader(new URL[]{plugin.toURI().toURL()});
//                        var clazz = loader.loadClass(PLUGIN_CLASS_NAME);
//                        var instance = clazz.getDeclaredConstructor().newInstance();
//                        clazz.getMethod("register").invoke(instance);
//                        dynamicModules.put(clazz, instance);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            log.error(Lang.ERR_CANNOT_LOAD_PLUGIN, e);
//        }
    }
}
