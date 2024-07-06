package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
public abstract class AbstractFeatureModule implements FeatureModule {
    @Getter
    @Autowired
    private PeerBanHelperServer server;
    @Getter
    private boolean register;

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
        ConfigurationSection section = Objects.requireNonNull(server.getProfileConfig().getConfigurationSection("module")).getConfigurationSection(getConfigName());
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
        server.getProfileConfig().set("module." + getConfigName(), getConfig());
        server.getProfileConfig().save(new File(Main.getConfigDirectory(), "profile.yml"));
    }
}
