package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.ConfigPair;
import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.List;

@Getter
@Setter
public class ModuleIPBlacklistConfigSection extends ModuleBaseConfigSection {

    private List<String> ips;
    private List<Integer> ports;

    public ModuleIPBlacklistConfigSection(ConfigPair configPair) {
        super(configPair, "ip-address-blocker");
    }

    @Override
    public void load() {
        super.load();
        ConfigurationSection section = getConfigSection();
        this.ips = section.getStringList("ips");
        this.ports = section.getIntList("ports");
    }

    @Override
    public void save() {
        super.save();
        ConfigurationSection section = getConfigSection();
        section.set("ips", ips);
        section.set("ports", ports);
        super.callSave();
    }

}
