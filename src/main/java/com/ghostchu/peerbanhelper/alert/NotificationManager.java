package com.ghostchu.peerbanhelper.alert;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.push.PushManagerImpl;
import com.ghostchu.peerbanhelper.util.push.PushProvider;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.JsonObject;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class NotificationManager implements Reloadable {
    private static final String CONFIG_KEY = "notification-routes";
    private static final String DESKTOP = "desktop";
    private final PushManagerImpl pushManager;
    private final Map<String, NotificationCategory> categories = new ConcurrentHashMap<>();
    private final Map<String, Channel> moduleChannels = new ConcurrentHashMap<>();
    private volatile Map<String, Set<String>> routes = Map.of();
    private final ThreadPoolExecutor deliveries = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), runnable -> {
        var thread = new Thread(runnable, "notification-delivery");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationManager(PushManagerImpl pushManager, List<NotificationCategory> initialCategories) {
        this.pushManager = pushManager;
        initialCategories.forEach(this::registerCategory);
        loadRoutes();
        Main.getReloadManager().register(this);
        deliveries.allowCoreThreadTimeOut(true);
    }

    /**
     * Registers a category supplied at runtime. Plugins should register from start(),
     * publish through AlertManager with the category ID, and unregister from stop().
     * Use a stable ID namespaced by the plugin ID. Routing rules survive unregistering.
     */
    public void registerCategory(NotificationCategory category) {
        if (categories.putIfAbsent(category.id(), category) != null) {
            throw new IllegalArgumentException("Notification category already registered: " + category.id());
        }
    }

    public void unregisterCategory(NotificationCategory category) {
        categories.remove(category.id(), category);
    }

    public void registerChannelType(String type, BiFunction<String, ConfigurationSection, PushProvider> yaml,
                                    BiFunction<String, JsonObject, PushProvider> json) {
        pushManager.registerProviderType(type, yaml, json);
    }

    public void registerChannel(String id, String name, Delivery delivery) {
        Objects.requireNonNull(delivery);
        if (moduleChannels.putIfAbsent("module:" + id, new Channel(name, delivery, false)) != null) {
            throw new IllegalArgumentException("Notification channel already registered: " + id);
        }
    }

    public void unregisterChannel(String id) {
        moduleChannels.remove("module:" + id);
    }

    @FunctionalInterface
    public interface Delivery {
        boolean send(String title, String content, AlertLevel level) throws Exception;
    }

    private record Channel(String name, Delivery delivery, boolean legacyExternal) {
    }

    public record ChannelOption(String id, String name) {
    }

    public record CategoryRule(NotificationCategory category, boolean useDefault, List<String> channelIds) {
    }

    public record Settings(List<ChannelOption> channels, List<CategoryRule> rules) {
    }

    NotificationCategory requireCategory(String id) {
        var category = categories.get(id);
        if (category == null) {
            throw new IllegalArgumentException("Unknown notification category: " + id);
        }
        return category;
    }

    public Settings settings() {
        var channels = channels();
        var current = routes;
        var options = channels.entrySet().stream()
                .map(entry -> new ChannelOption(entry.getKey(), entry.getValue().name()))
                .toList();
        var rules = categories.values().stream()
                .sorted(Comparator.comparing(NotificationCategory::group).thenComparing(NotificationCategory::id))
                .map(category -> new CategoryRule(category, !current.containsKey(category.id()),
                        selectedChannels(category, channels, current)))
                .toList();
        return new Settings(options, rules);
    }

    public synchronized void saveRule(String categoryId, boolean useDefault, Set<String> channelIds) throws IOException {
        requireCategory(categoryId);
        var next = new LinkedHashMap<>(routes);
        if (useDefault) {
            next.remove(categoryId);
        } else {
            var allowed = channels().keySet();
            var previous = routes.getOrDefault(categoryId, Set.of());
            if (channelIds.stream().anyMatch(id -> !allowed.contains(id) && !previous.contains(id))) {
                throw new IllegalArgumentException("Unknown notification channel");
            }
            next.put(categoryId, Set.copyOf(channelIds));
        }
        var config = Main.getMainConfig();
        synchronized (config) {
            var previous = config.get(CONFIG_KEY);
            var serialized = next.entrySet().stream()
                    .map(entry -> Map.of("category", entry.getKey(), "channels", entry.getValue().stream().sorted().toList()))
                    .toList();
            config.set(CONFIG_KEY, serialized);
            try {
                config.save(Main.getMainConfigFile());
            } catch (IOException e) {
                config.set(CONFIG_KEY, previous);
                throw e;
            }
        }
        routes = Map.copyOf(next);
    }

    private void loadRoutes() {
        var config = Main.getMainConfig();
        Map<String, Set<String>> loaded = new LinkedHashMap<>();
        synchronized (config) {
            for (var row : config.getMapList(CONFIG_KEY)) {
                if (row.get("category") instanceof String category && row.get("channels") instanceof List<?> ids) {
                    var channels = ids.stream().filter(String.class::isInstance).map(String.class::cast).toList();
                    loaded.put(category, Set.copyOf(channels));
                }
            }
        }
        routes = Map.copyOf(loaded);
    }

    private Map<String, Channel> channels() {
        Map<String, Channel> channels = new LinkedHashMap<>();
        channels.put(DESKTOP, new Channel("Desktop", (title, content, level) -> {
            var guiLevel = switch (level) {
                case ERROR, FATAL -> Level.ERROR;
                case WARN -> Level.WARN;
                default -> Level.INFO;
            };
            Main.getGuiManager().createNotification(guiLevel, title, content);
            return true;
        }, false));
        pushManager.getProviders().forEach((id, provider) ->
                channels.put(id, new Channel(provider.getName(), provider::push, true)));
        channels.putAll(moduleChannels);
        return channels;
    }

    private List<String> selectedChannels(NotificationCategory category, Map<String, Channel> channels,
                                          Map<String, Set<String>> current) {
        var configured = current.get(category.id());
        if (configured != null) {
            return configured.stream().sorted().toList();
        }
        var defaults = new ArrayList<String>();
        defaults.add(DESKTOP);
        if (category.defaultExternal()) {
            channels.forEach((id, channel) -> {
                if (channel.legacyExternal()) {
                    defaults.add(id);
                }
            });
        }
        return defaults;
    }

    void deliver(NotificationCategory category, AlertLevel level, TranslationComponent title, TranslationComponent content) {
        var channels = channels();
        var selected = selectedChannels(category, channels, routes);
        if (selected.isEmpty()) {
            return;
        }
        String localizedTitle = tlUI(title);
        String localizedContent = tlUI(content);
        for (String id : selected) {
            var channel = channels.get(id);
            if (channel == null) {
                continue;
            }
            if (DESKTOP.equals(id)) {
                send(category.id(), id, channel, localizedTitle, localizedContent, level);
                continue;
            }
            try {
                deliveries.execute(() -> send(category.id(), id, channel,
                        "[PeerBanHelper/" + level.name() + "] " + localizedTitle, localizedContent, level));
            } catch (RejectedExecutionException e) {
                log.warn("Notification delivery queue unavailable for category {} and channel {}", category.id(), id);
            }
        }
    }

    private void send(String category, String channelId, Channel channel, String title, String content, AlertLevel level) {
        try {
            if (!channel.delivery().send(title, content, level)) {
                log.warn("Notification delivery failed for category {} and channel {}", category, channelId);
            }
        } catch (Exception e) {
            log.warn("Notification delivery failed for category {} and channel {}", category, channelId, e);
        }
    }

    @Override
    public synchronized ReloadResult reloadModule() throws Exception {
        loadRoutes();
        return Reloadable.super.reloadModule();
    }

    @PreDestroy
    void close() {
        Main.getReloadManager().unregister(this);
        deliveries.shutdown();
    }
}
