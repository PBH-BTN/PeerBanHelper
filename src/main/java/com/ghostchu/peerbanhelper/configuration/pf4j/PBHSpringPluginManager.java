package com.ghostchu.peerbanhelper.configuration.pf4j;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginStateEvent;
import org.pf4j.PluginWrapper;
import org.pf4j.spring.ExtensionsInjector;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class PBHSpringPluginManager extends SpringPluginManager implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    public PBHSpringPluginManager() {
        super();
    }

    public PBHSpringPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
    }

    public PBHSpringPluginManager(List<Path> pluginsRoots) {
        super(pluginsRoots);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * This method load, start plugins and inject extensions in Spring
     */
    @PostConstruct
    public void init() {
        loadPlugins();
        startPlugins();

        AbstractAutowireCapableBeanFactory beanFactory = (AbstractAutowireCapableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        ExtensionsInjector extensionsInjector = new ExtensionsInjector(this, beanFactory);
        extensionsInjector.injectExtensions();
    }

    @Override
    public void loadPlugins() {
        log.debug("Lookup plugins in '{}'", pluginsRoots);

        // check for plugins roots
        if (pluginsRoots.isEmpty()) {
            log.warn("No plugins roots configured");
            return;
        }
        pluginsRoots.forEach(path -> {
            if (Files.notExists(path) || !Files.isDirectory(path)) {
                log.warn("No '{}' root", path);
            }
        });

        // get all plugin paths from repository
        List<Path> pluginPaths = pluginRepository.getPluginPaths();

        // check for no plugins
        if (pluginPaths.isEmpty()) {
            log.info("No plugins");
            return;
        }

        log.debug("Found {} possible plugins: {}", pluginPaths.size(), pluginPaths);

        // load plugins from plugin paths
        for (Path pluginPath : pluginPaths) {
            try {
                loadPluginFromPath(pluginPath);
            } catch (PluginRuntimeException e) {
                log.error("Cannot load plugin '{}'", pluginPath, e);
                Sentry.captureException(e);
            }
        }

        resolvePlugins();
    }

    @Override
    public void startPlugins() {
        for (PluginWrapper pluginWrapper : resolvedPlugins) {
            PluginState pluginState = pluginWrapper.getPluginState();
            if (!pluginState.isDisabled() && !pluginState.isStarted()) {
                try {
                    log.info("Start plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()));
                    pluginWrapper.getPlugin().start();
                    pluginWrapper.setPluginState(PluginState.STARTED);
                    pluginWrapper.setFailedException(null);
                    startedPlugins.add(pluginWrapper);
                } catch (Throwable e) {
                    pluginWrapper.setPluginState(PluginState.FAILED);
                    pluginWrapper.setFailedException(e);
                    log.error("Unable to start plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()), e);
                    Sentry.captureException(e);
                } finally {
                    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, pluginState));
                }
            }
        }
    }

    /**
     * Stop all active plugins.
     */
    @Override
    public void stopPlugins() {
        // stop started plugins in reverse order
        Collections.reverse(startedPlugins);
        Iterator<PluginWrapper> itr = startedPlugins.iterator();
        while (itr.hasNext()) {
            PluginWrapper pluginWrapper = itr.next();
            PluginState pluginState = pluginWrapper.getPluginState();
            if (pluginState.isStarted()) {
                try {
                    log.info("Stop plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()));
                    pluginWrapper.getPlugin().stop();
                    pluginWrapper.setPluginState(PluginState.STOPPED);
                    itr.remove();

                    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, pluginState));
                } catch (Throwable e) {
                    log.error("Unable to stop plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()), e);
                    Sentry.captureException(e);
                }
            } else {
                // do nothing
                log.debug("Plugin '{}' is not started, nothing to stop", getPluginLabel(pluginWrapper.getDescriptor()));
            }
        }
    }

    /**
     * Load a plugin from disk. If the path is a zip file, first unpack.
     *
     * @param pluginPath plugin location on disk
     * @return PluginWrapper for the loaded plugin or null if not loaded
     * @throws PluginRuntimeException if problems during load
     */
    @Override
    protected PluginWrapper loadPluginFromPath(Path pluginPath) {
        var platform = Main.getPlatform();
        if (platform != null) {
            try (var scanner = platform.getMalwareScanner()) {
                if (scanner != null) {
                    File file = pluginPath.toFile();
                    if (scanner.isMalicious(file)) {
                        throw new PluginRuntimeException(tlUI(Lang.MALWARE_SCANNER_DETECTED, "[JavaPlugin", pluginPath.toAbsolutePath()));
                    }
                }
            } catch (PluginRuntimeException e) {
                Sentry.captureException(e);
                throw e;
            } catch (Exception e) {
                log.debug("Malware scan failed for plugin '{}': Unable to close scanner", pluginPath, e);
                Sentry.captureException(e);
            }
        }
        return super.loadPluginFromPath(pluginPath);
    }
}
