package com.ghostchu.peerbanhelper.config;

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;

public class MainConfigUpdateScript {
    private final File file;
    private final YamlConfiguration configuration;

    public MainConfigUpdateScript(File file, YamlConfiguration configuration) {
        this.file = file;
        this.configuration = configuration;
    }
}
