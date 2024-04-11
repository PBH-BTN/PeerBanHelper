package com.ghostchu.peerbanhelper.config;

import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Getter
@Setter
public class ModuleBaseConfigSection extends BaseConfigSection {

    private boolean enabled;

    public ModuleBaseConfigSection(ConfigPair configPair, String sectionName) {
        super(configPair, sectionName);
    }

    @Override
    public void load() {
        ConfigurationSection section = getConfigSection();
        this.enabled = section.getBoolean("enabled");
    }

    @Override
    public void save() {
        ConfigurationSection section = getConfigSection();
        section.set("enabled", enabled);
        super.callSave();
    }

}
