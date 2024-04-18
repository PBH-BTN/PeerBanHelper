package com.ghostchu.peerbanhelper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildMeta {
    private String version = "unknown";
    private boolean nativeImage;
    private String os;
    private String branch;
    private String commit;
    private String abbrev;

    public void loadBuildMeta(YamlConfiguration configuration) {
        this.version = configuration.getString("maven.version");
        this.branch = configuration.getString("git.branch");
        this.commit = configuration.getString("git.commit.id.commit-id");
        this.abbrev = configuration.getString("git.commit.id.abbrev");
        this.nativeImage = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
        this.os = System.getProperty("os.name");
    }

    public String toString() {
        return "BuildMeta(version=" + this.getVersion() + ")";
    }
}
