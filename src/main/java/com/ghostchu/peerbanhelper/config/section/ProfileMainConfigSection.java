package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.BaseConfigSection;
import com.ghostchu.peerbanhelper.config.ConfigPair;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Getter
@Setter
public class ProfileMainConfigSection extends BaseConfigSection {

    private long checkInterval;
    private long banDuration;

    public ProfileMainConfigSection(ConfigPair configPair) {
        super(configPair, "");
    }

    @Override
    public void load() {
        ConfigurationSection section = getConfigSection();
        this.checkInterval = section.getLong("check-interval");
        this.banDuration = section.getLong("ban-duration");
    }

    @Override
    public void save() {
        ConfigurationSection section = getConfigSection();
        section.set("check-interval", checkInterval);
        section.set("ban-duration", banDuration);
        super.callSave();
    }

}
