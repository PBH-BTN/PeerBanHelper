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
     * Determines whether a specific experiment is activated based on the laboratory configuration.
     *
     * @param experiment The experiment to check for activation
     * @return true if the experiment is activated, false otherwise
     *
     * @implNote
     * - If the laboratory is not enabled, returns false
     * - If no configuration exists for the experiment, initializes a default configuration
     * - For "default" configuration, checks against the experimental group
     * - For explicit configuration, parses the boolean value directly
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
     * Sets the activation state for a specific experiment by its ID.
     *
     * @param id The unique identifier of the experiment to be activated or deactivated
     * @param activated The activation state to set. If null, defaults to "default" configuration
     * @throws IllegalArgumentException If the provided experiment ID does not exist in the Experiments registry
     *
     * This method allows configuring the activation status of an experiment. It searches through
     * the registered experiments, updates the configuration for the matching experiment, and saves
     * the configuration. If no matching experiment is found, an exception is thrown.
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
     * Saves the current laboratory configuration to the specified YAML configuration file.
     *
     * <p>This method attempts to persist the current state of the laboratory configuration
     * to the file system. If the save operation fails due to an I/O error, it logs an
     * informational message with the exception details.</p>
     *
     * @throws IOException if an I/O error occurs during the file saving process (caught internally)
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
