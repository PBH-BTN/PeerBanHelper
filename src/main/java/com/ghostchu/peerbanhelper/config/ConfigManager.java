package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.config.section.*;
import com.ghostchu.peerbanhelper.text.Lang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {

    private static final Map<ConfigType, ConfigPair> configs = new ConcurrentHashMap<>();
    private static final Map<SectionType, BaseConfigSection> sections = new ConcurrentHashMap<>();

    public static boolean initConfiguration() throws IOException {
        File configDirectory = Main.getConfigDirectory();
        File pluginDirectory = Main.getPluginDirectory();

        if (!configDirectory.exists() && !configDirectory.mkdirs())
            throw new IOException("Cannot create Configuration Directory correctly!");

        if (!configDirectory.isDirectory())
            throw new IllegalStateException(Lang.ERR_CONFIG_DIRECTORY_INCORRECT);

        if (!pluginDirectory.exists() && !pluginDirectory.mkdirs())
            throw new IOException("Cannot create Plugin Directory correctly!");

        boolean exists = true;
        File config = new File(configDirectory, "config.yml");
        File profile = new File(configDirectory, "profile.yml");
        if (!config.exists()) {
            exists = false;
            Files.copy(Objects.requireNonNull(Main.class.getResourceAsStream("/config.yml")), config.toPath());
        }
        if (!profile.exists()) {
            exists = false;
            Files.copy(Objects.requireNonNull(Main.class.getResourceAsStream("/profile.yml")), profile.toPath());
        }
        return exists;
    }

    public static void loadConfig() {
        configs.put(ConfigType.MAIN, new ConfigPair(new File(Main.getConfigDirectory(), "config.yml")));
        configs.put(ConfigType.PROFILE, new ConfigPair(new File(Main.getConfigDirectory(), "profile.yml")));
        configs.values().forEach(ConfigPair::loadYamlConfig);

        sections.put(SectionType.SERVER, new ServerConfigSection(getConfigPair(ConfigType.MAIN)));
        sections.put(SectionType.CLIENT, new ClientConfigSection(getConfigPair(ConfigType.MAIN)));
        sections.put(SectionType.LOGGER, new LoggerConfigSection(getConfigPair(ConfigType.MAIN)));
        sections.put(SectionType.THREADS, new ThreadsConfigSection(getConfigPair(ConfigType.MAIN)));
        sections.put(SectionType.PROFILE_MAIN, new ProfileMainConfigSection(getConfigPair(ConfigType.PROFILE)));
        sections.put(SectionType.MODULE_PEER_ID_BLACKLIST, new ModulePeerIdBlacklistConfigSection(getConfigPair(ConfigType.PROFILE)));
        sections.put(SectionType.MODULE_CLIENT_NAME_BLACKLIST, new ModuleClientNameBlacklistConfigSection(getConfigPair(ConfigType.PROFILE)));
        sections.put(SectionType.MODULE_IP_BLACKLIST, new ModuleIPBlacklistConfigSection(getConfigPair(ConfigType.PROFILE)));
        sections.put(SectionType.MODULE_PROGRESS_CHEAT_BLOCKER, new ModuleProgressCheatBlockerConfigSection(getConfigPair(ConfigType.PROFILE)));
        sections.put(SectionType.MODULE_ACTIVE_PROBING, new ModuleActiveProbingConfigSection(getConfigPair(ConfigType.PROFILE)));
        sections.put(SectionType.MODULE_AUTO_RANGE_BAN, new ModuleAutoRangeBanConfigSection(getConfigPair(ConfigType.PROFILE)));
        sections.values().forEach(BaseConfigSection::load);
    }

    public static void reloadConfig() {
        configs.values().forEach(ConfigPair::loadYamlConfig);
        sections.values().forEach(BaseConfigSection::load);
    }

    public static class Sections {
        private Sections() {
        }

        public static ServerConfigSection server() {
            return (ServerConfigSection) sections.get(SectionType.SERVER);
        }

        public static ClientConfigSection client() {
            return (ClientConfigSection) sections.get(SectionType.CLIENT);
        }

        public static LoggerConfigSection logger() {
            return (LoggerConfigSection) sections.get(SectionType.LOGGER);
        }

        public static ThreadsConfigSection threads() {
            return (ThreadsConfigSection) sections.get(SectionType.THREADS);
        }

        public static ProfileMainConfigSection profileMain() {
            return (ProfileMainConfigSection) sections.get(SectionType.PROFILE_MAIN);
        }

        public static ModulePeerIdBlacklistConfigSection modulePeerIdBlacklist() {
            return (ModulePeerIdBlacklistConfigSection) sections.get(SectionType.MODULE_PEER_ID_BLACKLIST);
        }

        public static ModuleClientNameBlacklistConfigSection moduleClientNameBlacklist() {
            return (ModuleClientNameBlacklistConfigSection) sections.get(SectionType.MODULE_CLIENT_NAME_BLACKLIST);
        }

        public static ModuleIPBlacklistConfigSection moduleIPBlacklist() {
            return (ModuleIPBlacklistConfigSection) sections.get(SectionType.MODULE_IP_BLACKLIST);
        }

        public static ModuleProgressCheatBlockerConfigSection moduleProgressCheatBlocker() {
            return (ModuleProgressCheatBlockerConfigSection) sections.get(SectionType.MODULE_PROGRESS_CHEAT_BLOCKER);
        }

        public static ModuleActiveProbingConfigSection moduleActiveProbing() {
            return (ModuleActiveProbingConfigSection) sections.get(SectionType.MODULE_ACTIVE_PROBING);
        }

        public static ModuleAutoRangeBanConfigSection moduleAutoRangeBan() {
            return (ModuleAutoRangeBanConfigSection) sections.get(SectionType.MODULE_AUTO_RANGE_BAN);
        }
    }

    public static ConfigPair getConfigPair(ConfigType type) {
        return configs.get(type);
    }

    public static BaseConfigSection getSection(SectionType type) {
        return sections.get(type);
    }

    public enum ConfigType {
        MAIN, PROFILE
    }

    public enum SectionType {
        SERVER,
        CLIENT,
        LOGGER,
        THREADS,
        PROFILE_MAIN,
        MODULE_PEER_ID_BLACKLIST,
        MODULE_CLIENT_NAME_BLACKLIST,
        MODULE_IP_BLACKLIST,
        MODULE_PROGRESS_CHEAT_BLOCKER,
        MODULE_ACTIVE_PROBING,
        MODULE_AUTO_RANGE_BAN

    }

}
