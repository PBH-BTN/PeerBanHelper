package com.ghostchu.peerbanhelper.config;

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;

public class ProfileUpdateScript {
    private final File file;
    private final YamlConfiguration configuration;

    public ProfileUpdateScript(File file, YamlConfiguration configuration) {
        this.file = file;
        this.configuration = configuration;
    }

    @UpdateScript(version = 1)
    public void addExcludeLists() {
        configuration.set("module.peer-id-blacklist.exclude-peer-id", Collections.emptyList());
        configuration.set("module.client-name-blacklist.exclude-client-name", Collections.emptyList());
    }
}
