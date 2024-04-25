package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

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
     * @return
     */
    public abstract boolean isConfigurable();

    @Override
    public ConfigurationSection getConfig() {
        if (!isConfigurable()) return null;
        ConfigurationSection section = profile.getConfigurationSection("module").getConfigurationSection(getConfigName());
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
        if (this instanceof PBHAPI pbhapi) {
            server.getWebManagerServer().unregister(pbhapi);
        }
    }

    @Override
    public void enable() {
        register = true;
        onEnable();
        log.info(Lang.MODULE_REGISTER, getName());
    }
}
