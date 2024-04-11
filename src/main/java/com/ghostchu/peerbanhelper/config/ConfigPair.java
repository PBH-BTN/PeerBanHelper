package com.ghostchu.peerbanhelper.config;

import lombok.Data;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

@Data
public class ConfigPair {

    private YamlConfiguration yamlConfig;
    private File file;

    public ConfigPair(File file) {
        this.file = file;
    }

    public void loadYamlConfig() {
        if (!file.exists() || !file.isFile())
            throw new RuntimeException("Cannot found config file: " + file.getName());
        yamlConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void callSave() throws IOException {
        yamlConfig.save(file);
    }

}
