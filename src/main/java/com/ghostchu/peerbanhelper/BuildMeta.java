package com.ghostchu.peerbanhelper;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

@Data
@NoArgsConstructor
public class BuildMeta {
    private String version = "unknown";

    public void loadBuildMeta(YamlConfiguration configuration) {
        this.version = configuration.getString("maven.version");
    }
}
