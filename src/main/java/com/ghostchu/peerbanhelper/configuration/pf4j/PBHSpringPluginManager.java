package com.ghostchu.peerbanhelper.configuration.pf4j;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.PluginModuleScope;
import com.ghostchu.peerbanhelper.text.Lang;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.*;
import org.pf4j.spring.SpringPlugin;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class PBHSpringPluginManager extends SpringPluginManager {
    private final ModuleManagerImpl modules;
    private final Map<String, Activation> activations = new HashMap<>();
    private final Set<String> starting = new HashSet<>();

    public PBHSpringPluginManager(Path root, String hostVersion, ModuleManagerImpl modules) {
        super(root);
        this.modules = modules;
        setSystemVersion(hostVersion);
    }

    @Override
    public void init() {
        // Spring's default @PostConstruct starts plugins while host beans are still being created.
        // PeerBanHelper starts them explicitly after its built-in modules are available.
    }

    public synchronized PluginDisplay getDisplay(String id) {
        var activation = activations.get(id);
        return activation == null ? null : activation.display;
    }

    @Override
    protected DependencyResolver.Result resolveDependencies() {
        // PF4J normally rejects the entire graph on one missing dependency or cycle.
        // Give its resolver the maximal runnable subset; keep the others visible and retryable.
        Map<String, PluginDescriptor> ready = new LinkedHashMap<>();
        List<PluginWrapper> pending = new ArrayList<>(getPlugins());
        int previousSize;
        do {
            previousSize = pending.size();
            pending.removeIf(plugin -> {
                for (var dependency : plugin.getDescriptor().getDependencies()) {
                    if (dependency.isOptional()) continue;
                    var descriptor = ready.get(dependency.getPluginId());
                    if (descriptor == null || !versionManager.checkVersionConstraint(
                            descriptor.getVersion(), dependency.getPluginVersionSupport())) return false;
                }
                ready.put(plugin.getPluginId(), plugin.getDescriptor());
                if (unresolvedPlugins.contains(plugin) && !plugin.getPluginState().isDisabled()) plugin.setFailedException(null);
                return true;
            });
        } while (pending.size() < previousSize);
        for (var plugin : pending) {
            plugin.setFailedException(new PluginRuntimeException("Unresolved dependencies for {}: {}",
                    plugin.getPluginId(), plugin.getDescriptor().getDependencies()));
            if (resolvedPlugins.remove(plugin)) unresolvedPlugins.add(plugin);
            log.warn("{}", plugin.getFailedException().getMessage());
        }
        return dependencyResolver.resolve(new ArrayList<>(ready.values()));
    }

    @Override
    protected boolean isPluginValid(PluginWrapper plugin) {
        if (super.isPluginValid(plugin)) return true;
        plugin.setFailedException(new PluginRuntimeException("Plugin {} requires PBH {}, running {}",
                plugin.getPluginId(), plugin.getDescriptor().getRequires(), getSystemVersion()));
        return false;
    }

    @Override
    protected void validatePluginDescriptor(PluginDescriptor descriptor) {
        super.validatePluginDescriptor(descriptor);
        // Parse version expressions before opening a classloader or adding the descriptor to the shared graph.
        try {
            versionManager.compareVersions(descriptor.getVersion(), descriptor.getVersion());
            versionManager.checkVersionConstraint(getSystemVersion(), descriptor.getRequires());
            for (var dependency : descriptor.getDependencies()) {
                versionManager.checkVersionConstraint(getSystemVersion(), dependency.getPluginVersionSupport());
            }
        } catch (RuntimeException e) {
            throw new PluginRuntimeException(e, "Invalid version in descriptor of {}", descriptor.getPluginId());
        }
    }

    @Override
    public synchronized void startPlugins() {
        Set<String> failures = new HashSet<>();
        for (var plugin : List.copyOf(resolvedPlugins)) {
            if (!plugin.getPluginState().isDisabled()) startPlugin(plugin.getPluginId(), failures);
        }
    }

    @Override
    public synchronized PluginState startPlugin(String id) {
        return startPlugin(id, new HashSet<>());
    }

    private PluginState startPlugin(String id, Set<String> failures) {
        checkPluginId(id);
        var plugin = getPlugin(id);
        var before = plugin.getPluginState();
        if (before.isStarted() || failures.contains(id) || !resolvedPlugins.contains(plugin)) return before;
        if (before.isDisabled() && !enablePlugin(id)) return before;
        if (!starting.add(id)) throw new PluginRuntimeException("Recursive plugin start: {}", id);
        try {
            for (var dependency : plugin.getDescriptor().getDependencies()) {
                var required = getPlugin(dependency.getPluginId());
                if (dependency.isOptional() && (required == null || starting.contains(dependency.getPluginId())
                        || required.getPluginState().isDisabled())) continue;
                if (required == null || required.getPluginState().isDisabled()
                        || !startPlugin(dependency.getPluginId(), failures).isStarted()) {
                    if (!dependency.isOptional()) throw new PluginRuntimeException("Dependency {} is not running", dependency.getPluginId());
                }
            }
            var loader = plugin.getPluginClassLoader();
            var activation = new Activation(modules.openPluginScope(loader));
            activations.put(id, activation);
            var entry = Objects.requireNonNull(plugin.getPlugin(), "PF4J could not construct the plugin entry");
            activation.entry = entry;
            entry.start();
            var context = activation.context;
            context.setParent(entry instanceof SpringPlugin spring ? spring.getApplicationContext() : getApplicationContext());
            context.setClassLoader(loader);
            context.getBeanFactory().registerSingleton("pluginWrapper", plugin);
            registerExtensions(plugin, context);
            context.refresh();
            context.getBeansOfType(FeatureModule.class).forEach((name, module) -> modules.register(module, name));
            activation.display = PluginDisplay.capture(entry);
            plugin.setPluginState(PluginState.STARTED);
            plugin.setFailedException(null);
            startedPlugins.add(plugin);
            activation.scope.activate();
            log.info("Started plugin {}", getPluginLabel(id));
        } catch (Exception | LinkageError | AssertionError e) {
            failures.add(id);
            if (activations.containsKey(id)) release(plugin, e);
            plugin.setPluginState(PluginState.FAILED);
            plugin.setFailedException(e);
            log.error("Could not start plugin {}", id, e);
        } finally {
            starting.remove(id);
        }
        firePluginStateEvent(new PluginStateEvent(this, plugin, before));
        return plugin.getPluginState();
    }

    private void registerExtensions(PluginWrapper plugin, AnnotationConfigApplicationContext context)
            throws IOException, ClassNotFoundException {
        var loader = plugin.getPluginClassLoader();
        var reader = new SimpleMetadataReaderFactory(loader);
        var ordered = new TreeMap<Integer, List<String>>();
        for (String name : new TreeSet<>(getExtensionClassNames(plugin.getPluginId()))) {
            // Inspect before loading: an optional extension may reference classes from an absent plugin.
            var extension = reader.getMetadataReader(name).getAnnotationMetadata().getAnnotations().get(Extension.class);
            if (extension.isPresent() && !Arrays.stream(extension.getStringArray("plugins"))
                    .allMatch(id -> getPlugin(id) != null && getPlugin(id).getPluginState().isStarted())) continue;
            int ordinal = extension.isPresent() ? extension.getInt("ordinal") : 0;
            ordered.computeIfAbsent(ordinal, ignored -> new ArrayList<>()).add(name);
        }
        for (var group : ordered.values()) {
            for (String name : group) {
                Class<?> type = loader.loadClass(name);
                if (type.getClassLoader() != loader) continue;
                var parent = context.getParent();
                if (parent != null && parent != getApplicationContext()) {
                    // A legacy SpringPlugin can already own this extension in its own context.
                    String[] existing = parent.getBeanNamesForType(type);
                    if (existing.length == 1) {
                        context.getBeanFactory().registerSingleton(name, parent.getBean(existing[0]));
                        continue;
                    }
                }
                context.registerBean(name, type);
            }
        }
    }

    @Override
    protected ExtensionFactory createExtensionFactory() {
        var fallback = super.createExtensionFactory();
        return new ExtensionFactory() {
            @Override
            public <T> T create(Class<T> type) {
                var owner = whichPlugin(type);
                var activation = owner == null ? null : activations.get(owner.getPluginId());
                if (activation != null && activation.context.isActive()
                        && activation.context.containsLocalBean(type.getName())) {
                    return activation.context.getBean(type.getName(), type);
                }
                return fallback.create(type);
            }
        };
    }

    @Override
    public synchronized void stopPlugins() {
        PluginRuntimeException failure = null;
        for (var plugin : List.copyOf(startedPlugins).reversed()) {
            try {
                stopPlugin(plugin.getPluginId());
            } catch (PluginRuntimeException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }

    @Override
    protected synchronized PluginState stopPlugin(String id, boolean stopDependents) {
        checkPluginId(id);
        var plugin = getPlugin(id);
        if (!plugin.getPluginState().isStarted()) return plugin.getPluginState();
        if (stopDependents) {
            for (String dependent : dependencyResolver.getDependents(id)) {
                if (stopPlugin(dependent, true).isStarted()) return PluginState.STARTED;
            }
        }
        var scope = activations.get(id).scope;
        try {
            if (!scope.drain(5, TimeUnit.SECONDS)) {
                var timeout = new PluginRuntimeException("Timed out waiting for running callbacks of plugin {}", id);
                plugin.setFailedException(timeout);
                throw timeout;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PluginRuntimeException(e, "Interrupted while stopping plugin {}", id);
        }
        Throwable failure = release(plugin, null);
        startedPlugins.remove(plugin);
        plugin.setFailedException(failure);
        plugin.setPluginState(failure == null ? PluginState.STOPPED : PluginState.FAILED);
        firePluginStateEvent(new PluginStateEvent(this, plugin, PluginState.STARTED));
        return plugin.getPluginState();
    }

    private Throwable release(PluginWrapper plugin, Throwable failure) {
        var activation = activations.remove(plugin.getPluginId());
        // Attempt every cleanup step, including after a partially completed start().
        for (Runnable cleanup : List.<Runnable>of(
                () -> modules.removePluginModules(plugin.getPluginClassLoader()),
                () -> { if (activation.entry != null) activation.entry.stop(); },
                activation.context::close)) {
            try {
                cleanup.run();
            } catch (Exception | LinkageError | AssertionError e) {
                if (failure == null) failure = e;
                else if (failure != e) failure.addSuppressed(e);
                log.error("Could not clean up plugin {}", plugin.getPluginId(), e);
            }
        }
        return failure;
    }

    @Override
    public synchronized boolean unloadPlugin(String id) {
        // PF4J's unload catches stop failures and still closes the classloader.
        if (getPlugin(id) == null || stopPlugin(id).isStarted()) return false;
        return super.unloadPlugin(id);
    }

    @Override
    public synchronized void unloadPlugins() {
        for (var plugin : getPlugins()) unloadPlugin(plugin.getPluginId());
    }

    private static final class Activation {
        final PluginModuleScope scope;
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Plugin entry;
        PluginDisplay display;

        Activation(PluginModuleScope scope) {
            this.scope = scope;
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
                        throw new PluginRuntimeException(tlUI(Lang.MALWARE_SCANNER_DETECTED, "[JavaPlugin]", pluginPath.toAbsolutePath()));
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
