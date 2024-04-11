package com.ghostchu.peerbanhelper.config;

import lombok.SneakyThrows;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

public abstract class BaseConfigSection {

    private final ConfigPair configPair;
    private final String sectionName;

    public BaseConfigSection(ConfigPair configPair, String sectionName) {
        this.configPair = configPair;
        this.sectionName = sectionName;
    }

    public ConfigurationSection getConfigSection() {
        return configPair.getYamlConfig().getConfigurationSection(sectionName);
    }

    public abstract void load();

    public void reload() {
        load();
    }

    public abstract void save();

    @SneakyThrows
    protected void callSave() {
        configPair.callSave();
    }

}
