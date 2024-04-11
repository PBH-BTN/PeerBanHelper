package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.ConfigPair;
import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.List;

@Getter
@Setter
public class ModuleClientNameBlacklistConfigSection extends ModuleBaseConfigSection {

    private List<String> bannedClientName;

    public ModuleClientNameBlacklistConfigSection(ConfigPair configPair) {
        super(configPair, "client-name-blacklist");
    }

    @Override
    public void load() {
        super.load();
        ConfigurationSection section = getConfigSection();
        this.bannedClientName = section.getStringList("banned-client-name");
    }

    @Override
    public void save() {
        super.save();
        ConfigurationSection section = getConfigSection();
        section.set("banned-client-name", bannedClientName);
        super.callSave();
    }

}
