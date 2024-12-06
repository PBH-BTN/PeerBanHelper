package com.ghostchu.peerbanhelper.lab;

import com.ghostchu.peerbanhelper.Main;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class Laboratory {
    // can be 0,1,2,3,4
    private final int experimentalGroup;
    private final YamlConfiguration labConfig;
    private final File labConfigFile;

    public Laboratory() throws IOException {
        String installationId = Main.getMainConfig().getString("installation-id", "???");
        int hashCode = installationId.hashCode();
        // Generate a number from hashCode in range 0-4
        this.experimentalGroup = Math.abs(hashCode % 5);
        this.labConfigFile = new File(Main.getConfigDirectory(), "laboratory.yml");
        if (!labConfigFile.exists()) {
            labConfigFile.createNewFile();
        }
        this.labConfig = YamlConfiguration.loadConfiguration(labConfigFile);
        for (Experiments value : Experiments.values()) {
            isExperimentActivated(value.getExperiment()); // 生成配置文件
        }
    }

    public boolean isExperimentActivated(Experiment experiment) {
        var value = labConfig.getString(experiment.getId());
        if (value == null) {
            labConfig.set(experiment.getId(), "default");
            value = "default";
            List<String> comments = new ArrayList<>();
            comments.addAll(Arrays.stream(tlUI(experiment.getTitle()).split("\n")).toList());
            comments.addAll(Arrays.stream(tlUI(experiment.getDescription()).split("\n")).toList());
            labConfig.setComments(experiment.getId(), comments);
            saveLabConfig();
        }
        if ("default".equals(value)) {
            return isExperimentalGroup(experiment.getGroup());
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    public void setExperimentActivated(String id, Boolean activated) throws IllegalArgumentException{
        for (Experiments value : Experiments.values()) {
            if(!value.getExperiment().getId().equals(id)){
                continue;
            }
            labConfig.set(value.getExperiment().getId(), activated == null ? "default" : activated);
            saveLabConfig();
            return;
        }
        throw new IllegalArgumentException("Invalid experiment id: "+id+", it's not exists in Experiments registry");
    }

    private void saveLabConfig() {
        try {
            labConfig.save(this.labConfigFile);
        } catch (IOException e) {
            log.info("Unable to save laboratory configuration", e);
        }
    }

    public int getExperimentalGroup() {
        return experimentalGroup;
    }

    public boolean isExperimentalGroup(List<Integer> group) {
        return group.contains(experimentalGroup);
    }
}
