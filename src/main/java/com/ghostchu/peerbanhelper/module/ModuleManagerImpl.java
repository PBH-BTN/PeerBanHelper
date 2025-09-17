package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.module.ModuleRegisterEvent;
import com.ghostchu.peerbanhelper.event.module.ModuleUnregisterEvent;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public final class ModuleManagerImpl implements ModuleManager {
    private final List<FeatureModule> modules = new ArrayList<>();
    @Autowired
    private ApplicationContext context;

    /**
     * 注册一个功能模块并启用它
     *
     * @param moduleClass 功能模块
     */
    @Override
    public void register(@NotNull Class<? extends FeatureModule> moduleClass) {
        // Main.registerBean(moduleClass, null);
        FeatureModule module = context.getBean(moduleClass);
        attemptRegister(module);
    }

    /**
     * 注册一个功能模块并启用它
     *
     * @param module 功能模块
     */
    @Override
    public void register(@NotNull FeatureModule module, String beanName) {
        //  Main.registerBean(module.getClass(), beanName);
        attemptRegister(module);
    }

    private void attemptRegister(FeatureModule module){
        if (module.isModuleEnabled()) {
            synchronized (modules) {
                var moduleRegisterEvent = new ModuleRegisterEvent(module);
                Main.getEventBus().post(moduleRegisterEvent);
                if (moduleRegisterEvent.isCancelled()) {
                    log.debug("Module {} registration cancelled: {}", module.getName(), moduleRegisterEvent.getCancelReason());
                    return;
                }
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
    @Override
    public boolean unregister(@NotNull FeatureModule module) {
        synchronized (modules) {
            var moduleUnregisterEvent = new ModuleUnregisterEvent(module);
            Main.getEventBus().post(moduleUnregisterEvent);
            module.disable();
            return this.modules.remove(module);
        }
    }

    /**
     * 解注册给定的 Class 的所有功能模块实例
     *
     * @param module 功能模块类
     */
    @Override
    public void unregister(@NotNull Class<FeatureModule> module) {
        synchronized (modules) {
            List<FeatureModule> moduleList = new ArrayList<>();
            for (FeatureModule featureModule : modules) {
                if (featureModule.getClass().equals(module)) {
                    moduleList.add(featureModule);
                }
            }
            moduleList.forEach(unregisteringModule -> {
                var moduleUnregisterEvent = new ModuleUnregisterEvent(unregisteringModule);
                Main.getEventBus().post(moduleUnregisterEvent);
                unregister(unregisteringModule);
            });
        }
    }

    /**
     * 解注册所有的功能模块
     */
    @Override
    public void unregisterAll() {
        List.copyOf(this.modules).forEach(unregisteringModule -> {
            var moduleUnregisterEvent = new ModuleUnregisterEvent(unregisteringModule);
            Main.getEventBus().post(moduleUnregisterEvent);
            unregister(unregisteringModule);
        });
    }

    /**
     * 获取注册的所有功能模块
     *
     * @return 功能模块列表（不可修改）
     */
    @Override
    public @NotNull List<FeatureModule> getModules() {
        return List.copyOf(modules);
    }

    /**
     * 从插件目录加载 JVM 插件
     */
    @Override
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
