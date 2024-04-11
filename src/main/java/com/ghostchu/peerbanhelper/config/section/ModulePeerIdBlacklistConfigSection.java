package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.ConfigPair;
import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.List;

@Getter
@Setter
public class ModulePeerIdBlacklistConfigSection extends ModuleBaseConfigSection {

    private List<String> bannedPeerId;

    public ModulePeerIdBlacklistConfigSection(ConfigPair configPair) {
        super(configPair, "peer-id-blacklist");
    }

    @Override
    public void load() {
        super.load();
        ConfigurationSection section = getConfigSection();
        this.bannedPeerId = section.getStringList("banned-peer-id");
    }

    @Override
    public void save() {
        super.save();
        ConfigurationSection section = getConfigSection();
        section.set("banned-peer-id", bannedPeerId);
        super.callSave();
    }
    
}
