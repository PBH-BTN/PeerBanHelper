package com.ghostchu.peerbanhelper.module;

import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

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
}
