package com.ghostchu.peerbanhelper.util.push;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.push.impl.*;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

@Component
@Slf4j
public final class PushManagerImpl implements Reloadable, PushManager {
    private static final String CONFIG_KEY = "push-notification";
    private final Map<String, ProviderFactory> factories = new ConcurrentHashMap<>();
    private volatile Map<String, PushProvider> providers = Map.of();

    public PushManagerImpl(HTTPUtil httpUtil) {
        registerFactory("pushplus", (name, yaml) -> PushPlusPushProvider.loadFromYaml(name, yaml, httpUtil),
                (name, json) -> PushPlusPushProvider.loadFromJson(name, json, httpUtil));
        registerFactory("serverchan", (name, yaml) -> ServerChanPushProvider.loadFromYaml(name, yaml, httpUtil),
                (name, json) -> ServerChanPushProvider.loadFromJson(name, json, httpUtil));
        registerFactory("smtp", (name, yaml) -> SmtpPushProvider.loadFromYaml(name, yaml),
                (name, json) -> SmtpPushProvider.loadFromJson(name, json));
        registerFactory("telegram", (name, yaml) -> TelegramPushProvider.loadFromYaml(name, yaml, httpUtil),
                (name, json) -> TelegramPushProvider.loadFromJson(name, json, httpUtil));
        registerFactory("bark", (name, yaml) -> BarkPushProvider.loadFromYaml(name, yaml, httpUtil),
                (name, json) -> BarkPushProvider.loadFromJson(name, json, httpUtil));
        registerFactory("pushdeer", (name, yaml) -> PushDeerPushProvider.loadFromYaml(name, yaml, httpUtil),
                (name, json) -> PushDeerPushProvider.loadFromJson(name, json, httpUtil));
        registerFactory("gotify", (name, yaml) -> GotifyPushProvider.loadFromYaml(name, yaml, httpUtil),
                (name, json) -> GotifyPushProvider.loadFromJson(name, json, httpUtil));
        registerFactory("ntfy", (name, yaml) -> NtfyPushProvider.loadFromYaml(name, yaml, httpUtil),
                (name, json) -> NtfyPushProvider.loadFromJson(name, json, httpUtil));
        registerFactory("webhook", (name, yaml) -> WebhookPushProvider.loadFromYaml(name, yaml, httpUtil),
                (name, json) -> WebhookPushProvider.loadFromJson(name, json, httpUtil));
        reloadConfig();
        Main.getReloadManager().register(this);
    }

    public synchronized void registerProviderType(String type, BiFunction<String, ConfigurationSection, PushProvider> yaml,
                                                  BiFunction<String, JsonObject, PushProvider> json) {
        registerFactory(type, yaml, json);
        reloadConfig();
    }

    private void registerFactory(String type, BiFunction<String, ConfigurationSection, PushProvider> yaml,
                                 BiFunction<String, JsonObject, PushProvider> json) {
        if (factories.putIfAbsent(type.toLowerCase(Locale.ROOT), new ProviderFactory(yaml, json)) != null) {
            throw new IllegalArgumentException("Push provider type already registered: " + type);
        }
    }

    private record ProviderFactory(BiFunction<String, ConfigurationSection, PushProvider> yaml,
                                   BiFunction<String, JsonObject, PushProvider> json) {
    }

    @Override
    public PushProvider createPushProvider(String name, String type, ConfigurationSection section) {
        var factory = factories.get(type.toLowerCase(Locale.ROOT));
        return factory == null ? null : factory.yaml().apply(name, section);
    }

    @Override
    public PushProvider createPushProvider(String name, String type, JsonObject json) {
        var factory = factories.get(type.toLowerCase(Locale.ROOT));
        return factory == null ? null : factory.json().apply(name, json);
    }

    private void reloadConfig() {
        Map<String, PushProvider> loaded = new LinkedHashMap<>();
        synchronized (Main.getMainConfig()) {
            var config = Main.getMainConfig().getConfigurationSection(CONFIG_KEY);
            if (config != null) {
                for (String name : config.getKeys(false)) {
                    try {
                        var section = config.getConfigurationSection(name);
                        var provider = createPushProvider(name, section.getString("type"), section);
                        if (provider != null) {
                            loaded.put(section.getString("channel-id", "push:" + name), provider);
                        }
                    } catch (Exception e) {
                        log.error("Unable to load push provider: {}", name, e);
                    }
                }
            }
        }
        providers = Collections.unmodifiableMap(loaded);
    }

    public Map<String, PushProvider> getProviders() {
        return providers;
    }

    @Override
    public List<PushProvider> getProviderList() {
        return List.copyOf(providers.values());
    }

    public synchronized boolean updateProvider(String previousName, PushProvider provider) throws IOException {
        var previousId = findId(previousName);
        var existingId = findId(provider.getName());
        if ((previousName != null && previousId == null)
                || (existingId != null && !existingId.equals(previousId))) {
            return false;
        }
        var next = new LinkedHashMap<>(providers);
        next.put(previousId == null ? "push:" + UUID.randomUUID() : previousId, provider);
        persist(next);
        return true;
    }

    public synchronized boolean deleteProvider(String name) throws IOException {
        var id = findId(name);
        if (id == null) {
            return false;
        }
        var next = new LinkedHashMap<>(providers);
        next.remove(id);
        persist(next);
        return true;
    }

    private String findId(String name) {
        return providers.entrySet().stream()
                .filter(entry -> entry.getValue().getName().equals(name))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private void persist(Map<String, PushProvider> next) throws IOException {
        var config = Main.getMainConfig();
        synchronized (config) {
            var previous = config.getConfigurationSection(CONFIG_KEY);
            var replacement = new MemoryConfiguration();
            if (previous != null) {
                var loadedNames = providers.values().stream().map(PushProvider::getName).toList();
                for (var name : previous.getKeys(false)) {
                    if (!loadedNames.contains(name)) {
                        var section = previous.getConfigurationSection(name);
                        if (section != null) {
                            replacement.createSection(name, section.getValues(true));
                        }
                    }
                }
            }
            next.forEach((id, provider) -> {
                var section = provider.saveYaml();
                section.set("channel-id", id);
                replacement.set(provider.getName(), section);
            });
            config.set(CONFIG_KEY, replacement);
            try {
                config.save(Main.getMainConfigFile());
            } catch (IOException e) {
                config.set(CONFIG_KEY, previous);
                throw e;
            }
        }
        providers = Collections.unmodifiableMap(next);
    }

    @Override
    public synchronized ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }
}