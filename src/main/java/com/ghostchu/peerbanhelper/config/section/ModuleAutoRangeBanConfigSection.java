package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.ConfigPair;
import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Getter
@Setter
public class ModuleAutoRangeBanConfigSection extends ModuleBaseConfigSection {

    private int ipv4;
    private int ipv6;

    public ModuleAutoRangeBanConfigSection(ConfigPair configPair) {
        super(configPair, "auto-range-ban");
    }

    @Override
    public void load() {
        super.load();
        ConfigurationSection section = getConfigSection();
        this.ipv4 = section.getInt("ipv4");
        this.ipv6 = section.getInt("ipv6");
    }

    @Override
    public void save() {
        super.save();
        ConfigurationSection section = getConfigSection();
        section.set("ipv4", ipv4);
        section.set("ipv6", ipv6);
        super.callSave();
    }

}
