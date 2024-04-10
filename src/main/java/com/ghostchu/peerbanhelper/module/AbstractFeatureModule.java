package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

@Slf4j
public abstract class AbstractFeatureModule implements FeatureModule {
    private final YamlConfiguration profile;

    public AbstractFeatureModule(YamlConfiguration profile) {
        this.profile = profile;
    }

    @Override
    public boolean isModuleEnabled() {
        return getConfig().getBoolean("enabled");
    }

    @Override
    public ConfigurationSection getConfig() {
        return profile.getConfigurationSection("module").getConfigurationSection(getConfigName());
    }

    private boolean register;

    @Override
    public void stop() {
        if (register) {
            log.info(Lang.MODULE_UNREGISTER, getName());
        }
    }

    @Override
    public void register() {
        register = true;
        log.info(Lang.MODULE_REGISTER, getName());
    }
}
