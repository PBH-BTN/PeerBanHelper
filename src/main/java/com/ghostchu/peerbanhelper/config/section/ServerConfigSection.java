package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.BaseConfigSection;
import com.ghostchu.peerbanhelper.config.ConfigPair;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Getter
@Setter
public class ServerConfigSection extends BaseConfigSection {

    private int port;
    private String address;
    private String prefix;

    public ServerConfigSection(ConfigPair configPair) {
        super(configPair, "server");
    }

    @Override
    public void load() {
        ConfigurationSection section = getConfigSection();
        this.port = section.getInt("http");
        this.address = section.getString("address");
        this.prefix = section.getString("prefix");
    }

    @Override
    public void save() {
        ConfigurationSection section = getConfigSection();
        section.set("http", port);
        section.set("address", address);
        section.set("prefix", prefix);
        super.callSave();
    }
}
