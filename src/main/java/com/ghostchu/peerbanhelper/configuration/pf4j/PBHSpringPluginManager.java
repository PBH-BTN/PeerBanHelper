package com.ghostchu.peerbanhelper.configuration.pf4j;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.ExtensionFactory;
import org.pf4j.PluginState;
import org.pf4j.PluginStateEvent;
import org.pf4j.PluginWrapper;
import org.pf4j.spring.ExtensionsInjector;
import org.pf4j.spring.SpringExtensionFactory;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
    protected ExtensionFactory createExtensionFactory() {
        return new SpringExtensionFactory(this);
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
                    log.error(e.getMessage(), e);
                }
            } else {
                // do nothing
                log.debug("Plugin '{}' is not started, nothing to stop", getPluginLabel(pluginWrapper.getDescriptor()));
            }
        }
    }
}
