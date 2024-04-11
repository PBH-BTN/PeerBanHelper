package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.BaseConfigSection;
import com.ghostchu.peerbanhelper.config.ConfigPair;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Getter
@Setter
public class LoggerConfigSection extends BaseConfigSection {

    private boolean hideFinishLog;

    public LoggerConfigSection(ConfigPair configPair) {
        super(configPair, "logger");
    }

    @Override
    public void load() {
        ConfigurationSection section = getConfigSection();
        this.hideFinishLog = section.getBoolean("hide-finish-log");
    }

    @Override
    public void save() {
        ConfigurationSection section = getConfigSection();
        section.set("hide-finish-log", hideFinishLog);
    }
}
