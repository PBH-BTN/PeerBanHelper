package com.ghostchu.peerbanhelper.plugin;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.api.event.Event;
import com.ghostchu.peerbanhelper.api.plugin.*;
import com.google.common.base.Preconditions;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles all plugin management from the Server
 */

@Slf4j
public final class SimplePluginManager implements PluginManager {
    private final Map<Pattern, PluginLoader> fileAssociations = new HashMap<>();
    private final List<Plugin> plugins = new ArrayList<>();
    private final Map<String, Plugin> lookupNames = new HashMap<>();
    private MutableGraph<String> dependencyGraph = GraphBuilder.directed().build();

    public SimplePluginManager() {
    }

    /**
     * Registers the specified plugin loader
     *
     * @param loader Class name of the PluginLoader to register
     * @throws IllegalArgumentException Thrown when the given Class is not a
     *     valid PluginLoader
     */
    @Override
    public void registerInterface(@NotNull Class<? extends PluginLoader> loader) throws IllegalArgumentException {
        PluginLoader instance;

        if (PluginLoader.class.isAssignableFrom(loader)) {
            Constructor<? extends PluginLoader> constructor;

            try {
                constructor = loader.getConstructor();
                instance = constructor.newInstance();
            } catch (NoSuchMethodException ex) {
                String className = loader.getName();
                throw new IllegalArgumentException(String.format("Class %s does not have a public %s(Server) constructor", className, className), ex);
            } catch (Exception ex) {
                throw new IllegalArgumentException(String.format("Unexpected exception %s while attempting to construct a new instance of %s", ex.getClass().getName(), loader.getName()), ex);
            }
        } else {
            throw new IllegalArgumentException(String.format("Class %s does not implement interface PluginLoader", loader.getName()));
        }

        Pattern[] patterns = instance.getPluginFileFilters();

        synchronized (this) {
            for (Pattern pattern : patterns) {
                fileAssociations.put(pattern, instance);
            }
        }
    }

    /**
     * Loads the plugins contained within the specified directory
     *
     * @param directory Directory to check for plugins
     * @return A list of all plugins loaded
     */
    @Override
    @NotNull
    public Plugin[] loadPlugins(@NotNull File directory) {
        Preconditions.checkArgument(directory != null, "Directory cannot be null");
        Preconditions.checkArgument(directory.isDirectory(), "Directory must be a directory");
        return loadPlugins(directory.listFiles());
    }

    /**
     * Loads the plugins in the list of the files
     *
     * @param files List of files containing plugins to load
     * @return A list of all plugins loaded
     */
    @NotNull
    public Plugin[] loadPlugins(@NotNull File[] files) {
        Preconditions.checkArgument(files != null, "File list cannot be null");

        List<Plugin> result = new ArrayList<>();
        Set<Pattern> filters = fileAssociations.keySet();

        Map<String, File> plugins = new HashMap<>();
        Set<String> loadedPlugins = new HashSet<>();
        Map<String, String> pluginsProvided = new HashMap<>();
        Map<String, Collection<String>> dependencies = new HashMap<>();
        Map<String, Collection<String>> softDependencies = new HashMap<>();

        // This is where it figures out all possible plugins
        for (File file : files) {
            PluginLoader loader = null;
            for (Pattern filter : filters) {
                Matcher match = filter.matcher(file.getName());
                if (match.find()) {
                    loader = fileAssociations.get(filter);
                }
            }

            if (loader == null) continue;

            PluginDescriptionFile description;
            try {
                description = loader.getPluginDescription(file);
                String name = description.getName();
                if (name.equalsIgnoreCase("peerbanhelper") || name.equalsIgnoreCase("pbh") || name.equalsIgnoreCase("ghostchu")) {
                    log.error("Could not load '{}': Restricted Name", file.getPath());
                    continue;
                } else if (description.getRawName().indexOf(' ') != -1) {
                    log.error("Could not load '{}': uses the space-character (0x20) in its name", file.getPath());
                    continue;
                }
            } catch (InvalidDescriptionException ex) {
                log.error("Could not load '{}'", file.getPath(), ex);
                continue;
            }

            File replacedFile = plugins.put(description.getName(), file);
            if (replacedFile != null) {
                log.error("Ambiguous plugin name `{}' for files `{}' and `{}'", description.getName(), file.getPath(), replacedFile.getPath());
            }

            String removedProvided = pluginsProvided.remove(description.getName());
            if (removedProvided != null) {
                log.warn("Ambiguous plugin name `{}'. It is also provided by `{}'", description.getName(), removedProvided);
            }

            for (String provided : description.getProvides()) {
                File pluginFile = plugins.get(provided);
                if (pluginFile != null) {
                    log.warn("`{} provides `{}' while this is also the name of `{}'", file.getPath(), provided, pluginFile.getPath());
                } else {
                    String replacedPlugin = pluginsProvided.put(provided, description.getName());
                    if (replacedPlugin != null) {
                        log.warn("`{}' is provided by both `{}' and `{}'", provided, description.getName(), replacedPlugin);
                    }
                }
            }

            Collection<String> softDependencySet = description.getSoftDepend();
            if (softDependencySet != null && !softDependencySet.isEmpty()) {
                if (softDependencies.containsKey(description.getName())) {
                    // Duplicates do not matter, they will be removed together if applicable
                    softDependencies.get(description.getName()).addAll(softDependencySet);
                } else {
                    softDependencies.put(description.getName(), new LinkedList<>(softDependencySet));
                }

                for (String depend : softDependencySet) {
                    dependencyGraph.putEdge(description.getName(), depend);
                }
            }

            Collection<String> dependencySet = description.getDepend();
            if (dependencySet != null && !dependencySet.isEmpty()) {
                dependencies.put(description.getName(), new LinkedList<>(dependencySet));

                for (String depend : dependencySet) {
                    dependencyGraph.putEdge(description.getName(), depend);
                }
            }

            Collection<String> loadBeforeSet = description.getLoadBefore();
            if (loadBeforeSet != null && !loadBeforeSet.isEmpty()) {
                for (String loadBeforeTarget : loadBeforeSet) {
                    if (softDependencies.containsKey(loadBeforeTarget)) {
                        softDependencies.get(loadBeforeTarget).add(description.getName());
                    } else {
                        // softDependencies is never iterated, so 'ghost' plugins aren't an issue
                        Collection<String> shortSoftDependency = new LinkedList<>();
                        shortSoftDependency.add(description.getName());
                        softDependencies.put(loadBeforeTarget, shortSoftDependency);
                    }

                    dependencyGraph.putEdge(loadBeforeTarget, description.getName());
                }
            }
        }

        while (!plugins.isEmpty()) {
            boolean missingDependency = true;
            Iterator<Map.Entry<String, File>> pluginIterator = plugins.entrySet().iterator();

            while (pluginIterator.hasNext()) {
                Map.Entry<String, File> entry = pluginIterator.next();
                String plugin = entry.getKey();

                if (dependencies.containsKey(plugin)) {
                    Iterator<String> dependencyIterator = dependencies.get(plugin).iterator();

                    while (dependencyIterator.hasNext()) {
                        String dependency = dependencyIterator.next();

                        // Dependency loaded
                        if (loadedPlugins.contains(dependency)) {
                            dependencyIterator.remove();

                            // We have a dependency not found
                        } else if (!plugins.containsKey(dependency) && !pluginsProvided.containsKey(dependency)) {
                            missingDependency = false;
                            pluginIterator.remove();
                            softDependencies.remove(plugin);
                            dependencies.remove(plugin);

                            log.error("Could not load '{}'", entry.getValue().getPath(), new UnknownDependencyException("Unknown dependency " + dependency + ". Please download and install " + dependency + " to run this plugin."));
                            break;
                        }
                    }

                    if (dependencies.containsKey(plugin) && dependencies.get(plugin).isEmpty()) {
                        dependencies.remove(plugin);
                    }
                }
                if (softDependencies.containsKey(plugin)) {

                    // Soft depend is no longer around
                    softDependencies.get(plugin).removeIf(softDependency -> !plugins.containsKey(softDependency) && !pluginsProvided.containsKey(softDependency));

                    if (softDependencies.get(plugin).isEmpty()) {
                        softDependencies.remove(plugin);
                    }
                }
                if (!(dependencies.containsKey(plugin) || softDependencies.containsKey(plugin)) && plugins.containsKey(plugin)) {
                    // We're clear to load, no more soft or hard dependencies left
                    File file = plugins.get(plugin);
                    pluginIterator.remove();
                    missingDependency = false;

                    try {
                        Plugin loadedPlugin = loadPlugin(file);
                        if (loadedPlugin != null) {
                            result.add(loadedPlugin);
                            loadedPlugins.add(loadedPlugin.getName());
                            loadedPlugins.addAll(loadedPlugin.getDescription().getProvides());
                        } else {
                            log.error("Could not load '{}'", file.getPath());
                        }
                    } catch (InvalidPluginException ex) {
                        log.error("Could not load '{}'", file.getPath(), ex);
                    }
                }
            }

            if (missingDependency) {
                // We now iterate over plugins until something loads
                // This loop will ignore soft dependencies
                pluginIterator = plugins.entrySet().iterator();

                while (pluginIterator.hasNext()) {
                    Map.Entry<String, File> entry = pluginIterator.next();
                    String plugin = entry.getKey();

                    if (!dependencies.containsKey(plugin)) {
                        softDependencies.remove(plugin);
                        missingDependency = false;
                        File file = entry.getValue();
                        pluginIterator.remove();

                        try {
                            Plugin loadedPlugin = loadPlugin(file);
                            if (loadedPlugin != null) {
                                result.add(loadedPlugin);
                                loadedPlugins.add(loadedPlugin.getName());
                                loadedPlugins.addAll(loadedPlugin.getDescription().getProvides());
                            } else {
                                log.error("Could not load '{}'", file.getPath());
                            }
                            break;
                        } catch (InvalidPluginException ex) {
                            log.error("Could not load '{}'", file.getPath(), ex);
                        }
                    }
                }
                // We have no plugins left without a depend
                if (missingDependency) {
                    softDependencies.clear();
                    dependencies.clear();
                    Iterator<File> failedPluginIterator = plugins.values().iterator();

                    while (failedPluginIterator.hasNext()) {
                        File file = failedPluginIterator.next();
                        failedPluginIterator.remove();
                        log.error("Could not load '{}': circular dependency detected", file.getPath());
                    }
                }
            }
        }

        return result.toArray(new Plugin[0]);
    }

    /**
     * Loads the plugin in the specified file
     * <p>
     * File must be valid according to the current enabled Plugin interfaces
     *
     * @param file File containing the plugin to load
     * @return The Plugin loaded, or null if it was invalid
     * @throws InvalidPluginException Thrown when the specified file is not a
     *     valid plugin
     * @throws UnknownDependencyException If a required dependency could not
     *     be found
     */
    @Override
    @Nullable
    public synchronized Plugin loadPlugin(@NotNull File file) throws InvalidPluginException, UnknownDependencyException {
        Preconditions.checkArgument(file != null, "File cannot be null");

        Set<Pattern> filters = fileAssociations.keySet();
        Plugin result = null;

        for (Pattern filter : filters) {
            String name = file.getName();
            Matcher match = filter.matcher(name);

            if (match.find()) {
                PluginLoader loader = fileAssociations.get(filter);

                result = loader.loadPlugin(file);
            }
        }

        if (result != null) {
            plugins.add(result);
            lookupNames.put(result.getDescription().getName(), result);
            for (String provided : result.getDescription().getProvides()) {
                lookupNames.putIfAbsent(provided, result);
            }
        }

        return result;
    }

    /**
     * Checks if the given plugin is loaded and returns it when applicable
     * <p>
     * Please note that the name of the plugin is case-sensitive
     *
     * @param name Name of the plugin to check
     * @return Plugin if it exists, otherwise null
     */
    @Override
    @Nullable
    public synchronized Plugin getPlugin(@NotNull String name) {
        return lookupNames.get(name.replace(' ', '_'));
    }

    @Override
    @NotNull
    public synchronized Plugin[] getPlugins() {
        return plugins.toArray(new Plugin[0]);
    }

    /**
     * Checks if the given plugin is enabled or not
     * <p>
     * Please note that the name of the plugin is case-sensitive.
     *
     * @param name Name of the plugin to check
     * @return true if the plugin is enabled, otherwise false
     */
    @Override
    public boolean isPluginEnabled(@NotNull String name) {
        Plugin plugin = getPlugin(name);

        return isPluginEnabled(plugin);
    }

    /**
     * Checks if the given plugin is enabled or not
     *
     * @param plugin Plugin to check
     * @return true if the plugin is enabled, otherwise false
     */
    @Override
    public boolean isPluginEnabled(@Nullable Plugin plugin) {
        if ((plugin != null) && (plugins.contains(plugin))) {
            return plugin.isEnabled();
        } else {
            return false;
        }
    }

    @Override
    public void enablePlugin(@NotNull final Plugin plugin) {
        if (!plugin.isEnabled()) {
            try {
                plugin.getPluginLoader().enablePlugin(plugin);
            } catch (Throwable ex) {
                log.error("Error occurred (in the plugin loader) while enabling {} (Is it up to date?)", plugin.getDescription().getFullName(), ex);
            }
        }
    }

    @Override
    public void disablePlugins() {
        Plugin[] plugins = getPlugins();
        for (int i = plugins.length - 1; i >= 0; i--) {
            disablePlugin(plugins[i]);
        }
    }

    @Override
    public void disablePlugin(@NotNull final Plugin plugin) {
        if (plugin.isEnabled()) {
            try {
                plugin.getPluginLoader().disablePlugin(plugin);
            } catch (Throwable ex) {
                log.error("Error occurred (in the plugin loader) while disabling {} (Is it up to date?)", plugin.getDescription().getFullName(), ex);
            }
            unregisterListeners(plugin);
        }
    }

    @Override
    public void clearPlugins() {
        synchronized (this) {
            disablePlugins();
            plugins.clear();
            lookupNames.clear();
            dependencyGraph = GraphBuilder.directed().build();
            Main.getPluginRegisteredEventListeners().values().forEach(set -> set.forEach(obj -> Main.getEventBus().unregister(obj)));
            fileAssociations.clear();
        }
    }

    /**
     * Calls an event with the given details.
     *
     * @param event Event details
     */
    @Override
    public void callEvent(@NotNull Event event) {
        fireEvent(event);
    }

    private void fireEvent(@NotNull Event event) {
        Main.getEventBus().post(event);
    }

    @Override
    public void registerListeners(@NotNull Plugin plugin, @NotNull Listener listener) {
        Set<Listener> listeners = Main.getPluginRegisteredEventListeners().get(plugin);
        if (listeners.add(listener)) {
            Main.getEventBus().register(listener);
        }
    }

    @Override
    public void unregisterListeners(@NotNull Plugin plugin) {
        Main.getPluginRegisteredEventListeners().get(plugin).forEach(listener -> Main.getEventBus().unregister(listener));
        Main.getPluginRegisteredEventListeners().entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }


    @Override
    public void unregisterListener(@NotNull Listener listener) {
        Iterator<Map.Entry<Plugin, Set<Listener>>> it = Main.getPluginRegisteredEventListeners().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Plugin, Set<Listener>> entry = it.next();
            if (entry.getValue().remove(listener)) {
                Main.getEventBus().unregister(listener);
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    public boolean isTransitiveDepend(@NotNull PluginDescriptionFile plugin, @NotNull PluginDescriptionFile depend) {
        Preconditions.checkArgument(plugin != null, "plugin");
        Preconditions.checkArgument(depend != null, "depend");

        if (dependencyGraph.nodes().contains(plugin.getName())) {
            Set<String> reachableNodes = Graphs.reachableNodes(dependencyGraph, plugin.getName());
            if (reachableNodes.contains(depend.getName())) {
                return true;
            }
            for (String provided : depend.getProvides()) {
                if (reachableNodes.contains(provided)) {
                    return true;
                }
            }
        }
        return false;
    }
}

