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

    /**
     * Determines whether a specific experiment is activated based on configuration and experimental group.
     *
     * @param experiment The experiment to check for activation
     * @return {@code true} if the experiment is activated, {@code false} otherwise
     *
     * @implNote This method handles the following scenarios:
     * - If laboratory features are disabled, returns {@code false}
     * - If experiment configuration is not set, initializes it with default settings
     * - For "default" configuration, checks against the current experimental group
     * - For explicit configuration, uses the specified boolean value
     */
    public boolean isExperimentActivated(Experiment experiment) {
        if (!isEnabled()) {
            return false;
        }
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

    /**
     * Sets the activation state for a specific experiment in the laboratory configuration.
     *
     * @param id The unique identifier of the experiment to be activated or deactivated
     * @param activated The activation state to set for the experiment:
     *                  - {@code true} to activate the experiment
     *                  - {@code false} to deactivate the experiment
     *                  - {@code null} to reset to default state
     * @throws IllegalArgumentException If the provided experiment ID does not exist in the Experiments registry
     *
     * @see Experiments
     */
    public void setExperimentActivated(String id, Boolean activated) throws IllegalArgumentException {
        for (Experiments value : Experiments.values()) {
            if (!value.getExperiment().getId().equals(id)) {
                continue;
            }
            labConfig.set(value.getExperiment().getId(), activated == null ? "default" : activated);
            saveLabConfig();
            return;
        }
        throw new IllegalArgumentException("Invalid experiment id: " + id + ", it's not exists in Experiments registry");
    }

    /**
     * Saves the current laboratory configuration to the configuration file.
     *
     * @throws IOException if an I/O error occurs while attempting to save the configuration file
     *                     (logged as an info-level message)
     */
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

    public void setEnabled(boolean enabled) {
        labConfig.set("enabled", enabled);
        saveLabConfig();
    }

    public boolean isEnabled() {
        return labConfig.getBoolean("enabled");
    }
}
