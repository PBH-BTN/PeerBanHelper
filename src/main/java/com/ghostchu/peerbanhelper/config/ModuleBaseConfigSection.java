package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.ModuleManager;
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

    protected ModuleManager moduleManager() {
        return Main.getServer().getModuleManager();
    }

}
