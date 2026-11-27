package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.module.ModuleRegisterEvent;
import com.ghostchu.peerbanhelper.event.module.ModuleUnregisterEvent;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.pf4j.PluginClassLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public final class ModuleManagerImpl implements ModuleManager {
    private final List<FeatureModule> modules = new ArrayList<>();
    private final Map<ClassLoader, PluginModuleScope> pluginScopes = new IdentityHashMap<>();
    @Autowired
    private ApplicationContext context;

    /**
     * 注册一个功能模块并启用它
     *
     * @param moduleClass 功能模块
     */
    @Override
    public void register(@NotNull Class<? extends FeatureModule> moduleClass) {
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
        attemptRegister(module);
    }

    private void attemptRegister(FeatureModule module) {
        synchronized (modules) {
            var loader = module.getClass().getClassLoader();
            if (loader instanceof PluginClassLoader) {
                var scope = pluginScopes.get(loader);
                if (scope == null || scope.isClosing()) {
                    throw new IllegalStateException("Plugin is not accepting modules: " + module.getClass().getName());
                }
                if (module instanceof AbstractFeatureModule feature) feature.bindPluginScope(scope);
            }
            if (modules.contains(module)) return;
            modules.add(module);

            // 如果配置启用，则真正启用模块
            if (module.shouldModuleEnabled()) {
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
            if (!modules.remove(module)) return false;
            Main.getEventBus().post(new ModuleUnregisterEvent(module));
            try {
                module.disable();
            } catch (Exception e) {
                log.warn("Unable to unregister module {}", module.getName(), e);
                Sentry.captureException(e);
            }
            return true;
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
            moduleList.forEach(this::unregister);
        }
    }

    @Override
    public void unregisterAll() {
        synchronized (modules) {
            List.copyOf(modules).forEach(this::unregister);
        }
    }

    public PluginModuleScope openPluginScope(ClassLoader loader) {
        synchronized (modules) {
            var scope = new PluginModuleScope();
            if (pluginScopes.putIfAbsent(loader, scope) != null) {
                throw new IllegalStateException("Plugin module scope is already open");
            }
            return scope;
        }
    }

    public void removePluginModules(ClassLoader loader) {
        synchronized (modules) {
            var owned = modules.stream().filter(m -> m.getClass().getClassLoader() == loader).toList();
            pluginScopes.remove(loader);
            modules.removeAll(owned);
            IllegalStateException failure = null;
            for (var module : owned.reversed()) {
                try {
                    Main.getEventBus().post(new ModuleUnregisterEvent(module));
                    module.disable();
                } catch (Exception | LinkageError | AssertionError e) {
                    if (failure == null) failure = new IllegalStateException("Plugin module cleanup failed", e);
                    else failure.addSuppressed(e);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private boolean isAvailable(FeatureModule module) {
        var scope = pluginScopes.get(module.getClass().getClassLoader());
        return (scope == null || scope.isActive()) && module.getModuleStatus().getType() == ModuleStatusType.ENABLED;
    }

    @Override
    public @NotNull List<FeatureModule> getModules() {
        synchronized (modules) {
            return modules.stream().filter(this::isAvailable).toList();
        }
    }

    @Override
    public <T extends FeatureModule> List<ModuleInvocation<T>> getInvocations(Class<T> type) {
        synchronized (modules) {
            return modules.stream().filter(type::isInstance).map(type::cast).filter(this::isAvailable)
                    .map(m -> new ModuleInvocation<>(m, m.getName(), pluginScopes.get(m.getClass().getClassLoader())))
                    .toList();
        }
    }

    @Override
    public void reloadModuleStates() {
        synchronized (modules) {
            for (FeatureModule module : List.copyOf(modules)) {
                var scope = pluginScopes.get(module.getClass().getClassLoader());
                if (scope != null && !scope.isActive()) continue;
                try {
                    if (module.shouldModuleEnabled()) {
                        var event = new ModuleRegisterEvent(module);
                        Main.getEventBus().post(event);
                        if (!event.isCancelled()) module.enable();
                    } else {
                        Main.getEventBus().post(new ModuleUnregisterEvent(module));
                        module.disable();
                    }
                } catch (Throwable e) {
                    log.error("Unable to reload module {}", module.getName(), e);
                }
            }
        }
    }
}
