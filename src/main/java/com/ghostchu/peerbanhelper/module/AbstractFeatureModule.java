package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

@Slf4j
public abstract class AbstractFeatureModule implements FeatureModule {
    private final YamlConfiguration profile;
    @Getter
    private final PeerBanHelperServer server;
    @Getter
    private boolean register;

    public AbstractFeatureModule(PeerBanHelperServer server, YamlConfiguration profile) {
        this.server = server;
        this.profile = profile;
    }

    @Override
    public boolean isModuleEnabled() {
        try {
            return !isConfigurable() || getConfig().getBoolean("enabled");
        } catch (Exception e) {
            log.warn(Lang.CONFIGURATION_OUTDATED_MODULE_DISABLED, getName());
            return false;
        }
    }

    /**
     * 如果返回 false，则不初始化任何配置文件相关对象
     *
     * @return 是否支持使用配置文件进行配置
     */
    public abstract boolean isConfigurable();

    @Override
    public ConfigurationSection getConfig() {
        if (!isConfigurable()) return null;
        ConfigurationSection section = Objects.requireNonNull(profile.getConfigurationSection("module")).getConfigurationSection(getConfigName());
        if (section == null) {
            log.warn(Lang.CONFIGURATION_OUTDATED_MODULE_DISABLED, getName());
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("enabled", false);
            return configuration;
        }
        return section;
    }

    @Override
    public void disable() {
        if (register) {
            onDisable();
            cleanupResources();
            log.info(Lang.MODULE_UNREGISTER, getName());
        }
    }

    private void cleanupResources() {

    }

    @Override
    public void enable() {
        register = true;
        onEnable();
        log.info(Lang.MODULE_REGISTER, getName());
    }

    @Override
    public void saveConfig() throws IOException {
        if (!isConfigurable()) return;
        profile.set("module." + getConfigName(), getConfig());
        profile.save(new File(Main.getConfigDirectory(), "profile.yml"));
    }
}
