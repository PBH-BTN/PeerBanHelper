package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.module.ModuleRegisterEvent;
import com.ghostchu.peerbanhelper.event.module.ModuleUnregisterEvent;
import io.sentry.Sentry;
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
        try {
            FeatureModule module = context.getBean(moduleClass);
            attemptRegister(module);
        } catch (Exception e) {
            log.warn("Unable to register feature module", e);
            Sentry.captureException(e);
        }
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

    private void attemptRegister(FeatureModule module) {
        synchronized (modules) {
            // 添加到模块列表（不管是否启用）
            if (!modules.contains(module)) {
                this.modules.add(module);
            }

            // 如果配置启用，则真正启用模块
            if (module.isModuleEnabled()) {
                var moduleRegisterEvent = new ModuleRegisterEvent(module);
                Main.getEventBus().post(moduleRegisterEvent);
                if (moduleRegisterEvent.isCancelled()) {
                    log.debug("Module {} registration cancelled: {}", module.getName(), moduleRegisterEvent.getCancelReason());
                    return;
                }
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
            try {
                module.disable();
            } catch (Exception e) {
                log.warn("Unable to unregister module {}", module.getName(), e);
                Sentry.captureException(e);
            }
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
        // 只返回实际已启用的模块
        synchronized (modules) {
            return modules.stream()
                    .filter(FeatureModule::isActuallyEnabled)
                    .toList();
        }
    }

    /**
     * 重新加载所有模块的启用状态
     * 根据配置文件的 enabled 字段，自动启用或禁用模块
     */
    @Override
    public void reloadModuleStates() {
        log.info("Reloading module states based on configuration...");

        // 直接遍历所有已知的模块
        List<FeatureModule> modulesCopy;
        synchronized (modules) {
            modulesCopy = new ArrayList<>(modules);
        }

        for (FeatureModule module : modulesCopy) {
            boolean shouldBeEnabled = module.isModuleEnabled();
            boolean isCurrentlyEnabled = module.isActuallyEnabled();

            if (shouldBeEnabled && !isCurrentlyEnabled) {
                // 模块应该启用但当前未启用，启用它
                log.info("Enabling module {} due to configuration change", module.getName());
                var moduleRegisterEvent = new ModuleRegisterEvent(module);
                Main.getEventBus().post(moduleRegisterEvent);
                if (moduleRegisterEvent.isCancelled()) {
                    log.debug("Module {} registration cancelled: {}", module.getName(), moduleRegisterEvent.getCancelReason());
                    continue;
                }
                module.enable();
            } else if (!shouldBeEnabled && isCurrentlyEnabled) {
                // 模块不应该启用但当前已启用，禁用它
                log.info("Disabling module {} due to configuration change", module.getName());
                var moduleUnregisterEvent = new ModuleUnregisterEvent(module);
                Main.getEventBus().post(moduleUnregisterEvent);
                module.disable();
            }
        }

        log.info("Module states reloaded successfully");
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
