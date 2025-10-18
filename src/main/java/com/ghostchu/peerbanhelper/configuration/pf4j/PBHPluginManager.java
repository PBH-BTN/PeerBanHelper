package com.ghostchu.peerbanhelper.configuration.pf4j;

import org.pf4j.*;
import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PBHPluginManager extends AbstractPluginManager {

    private static final Logger log = LoggerFactory.getLogger(PBHPluginManager.class);

    public static final String PLUGINS_DIR_CONFIG_PROPERTY_NAME = "pf4j.pluginsConfigDir";

    public PBHPluginManager() {
        super();
    }

    public PBHPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
    }

    public PBHPluginManager(List<Path> pluginsRoots) {
        super(pluginsRoots);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new CompoundPluginDescriptorFinder()
                .add(new PropertiesPluginDescriptorFinder())
                .add(new ManifestPluginDescriptorFinder());
    }

    @Override
    protected ExtensionFinder createExtensionFinder() {
        DefaultExtensionFinder extensionFinder = new DefaultExtensionFinder(this);
        addPluginStateListener(extensionFinder);

        return extensionFinder;
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new DefaultPluginFactory();
    }

    @Override
    protected ExtensionFactory createExtensionFactory() {
        return new DefaultExtensionFactory();
    }

    @Override
    protected PluginStatusProvider createPluginStatusProvider() {
        String configDir = System.getProperty(PLUGINS_DIR_CONFIG_PROPERTY_NAME);
        Path configPath = configDir != null
                ? Paths.get(configDir)
                : getPluginsRoots().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No pluginsRoot configured"));

        return new DefaultPluginStatusProvider(configPath);
    }

    @Override
    protected PluginRepository createPluginRepository() {
        return new CompoundPluginRepository()
                .add(new DevelopmentPluginRepository(getPluginsRoots()), this::isDevelopment)
                .add(new JarPluginRepository(getPluginsRoots()), this::isNotDevelopment)
                .add(new DefaultPluginRepository(getPluginsRoots()), this::isNotDevelopment);
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return new CompoundPluginLoader()
                .add(new DevelopmentPluginLoader(this), this::isDevelopment)
                .add(new JarPluginLoader(this), this::isNotDevelopment)
                .add(new DefaultPluginLoader(this), this::isNotDevelopment);
    }

    @Override
    protected VersionManager createVersionManager() {
        return new DefaultVersionManager();
    }

    @Override
    protected void initialize() {
        super.initialize();

        if (isDevelopment()) {
            addPluginStateListener(new LoggingPluginStateListener());
        }

        log.info("PF4J version {} in '{}' mode", getVersion(), getRuntimeMode());
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
        // First unzip any ZIP files
        try {
            pluginPath = FileUtils.expandIfZip(pluginPath);
        } catch (Exception e) {
            log.warn("Failed to unzip " + pluginPath, e);
            return null;
        }

        return super.loadPluginFromPath(pluginPath);
    }
}
