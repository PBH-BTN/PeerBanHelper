package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.ConfigManager;
import com.ghostchu.peerbanhelper.config.ConfigPair;
import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import com.ghostchu.peerbanhelper.module.impl.ProgressCheatBlocker;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Getter
@Setter
public class ModuleProgressCheatBlockerConfigSection extends ModuleBaseConfigSection {

    private long minimumSize;
    private double maximumDifference;
    private double rewindMaximumDifference;
    private boolean blockExcessiveClients;
    private double excessiveThreshold;

    public ModuleProgressCheatBlockerConfigSection(ConfigPair configPair) {
        super(configPair, "progress-cheat-blocker");
    }

    @Override
    public void load() {
        super.load();
        ConfigurationSection section = getConfigSection();
        this.minimumSize = section.getLong("minimum-size");
        this.maximumDifference = section.getDouble("maximum-difference");
        this.rewindMaximumDifference = section.getDouble("rewind-maximum-difference");
        this.blockExcessiveClients = section.getBoolean("block-excessive-clients");
        this.excessiveThreshold = section.getDouble("excessive-threshold");
    }

    @Override
    public void save() {
        super.save();
        ConfigurationSection section = getConfigSection();
        section.set("minimum-size", minimumSize);
        section.set("maximum-difference", maximumDifference);
        section.set("rewind-maximum-difference", rewindMaximumDifference);
        section.set("block-excessive-clients", blockExcessiveClients);
        section.set("excessive-threshold", excessiveThreshold);
        super.callSave();
    }

    @Override
    public void reload() {
        super.reload();
        moduleManager().getModulesByClass(ProgressCheatBlocker.class).forEach(module -> moduleManager().unregisterModule(module));
        moduleManager().registerModule(new ProgressCheatBlocker(ConfigManager.Sections.moduleProgressCheatBlocker()));
    }

}
