package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.BaseConfigSection;
import com.ghostchu.peerbanhelper.config.ConfigPair;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Getter
@Setter
public class ThreadsConfigSection extends BaseConfigSection {

    private int generalParallelism;
    private int checkBanParallelism;
    private int ruleExecuteParallelism;
    private int downloaderApiParallelism;

    public ThreadsConfigSection(ConfigPair configPair) {
        super(configPair, "threads");
    }

    @Override
    public void load() {
        ConfigurationSection section = getConfigSection();
        this.generalParallelism = section.getInt("general-parallelism");
        this.checkBanParallelism = section.getInt("check-ban-parallelism");
        this.ruleExecuteParallelism = section.getInt("rule-execute-parallelism");
        this.downloaderApiParallelism = section.getInt("downloader-api-parallelism");
    }

    @Override
    public void save() {
        ConfigurationSection section = getConfigSection();
        section.set("general-parallelism", generalParallelism);
        section.set("check-ban-parallelism", checkBanParallelism);
        section.set("rule-execute-parallelism", ruleExecuteParallelism);
        section.set("downloader-api-parallelism", downloaderApiParallelism);
        super.callSave();
    }
}
