package com.ghostchu.peerbanhelper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class BuildMeta {
    private String version = "unknown";
    private String os;
    private String branch;
    private String commit;
    private String abbrev;
    private String javafx;
    private String compileTime;

    public void loadBuildMeta(YamlConfiguration configuration) {
        this.version = configuration.getString("maven.version");
        this.branch = configuration.getString("git.branch");
        this.commit = configuration.getString("git.commit.id.commit-id");
        this.abbrev = configuration.getString("git.commit.id.abbrev");
        this.os = System.getProperty("os.name");
        this.javafx = configuration.getString("javafx.version");
        this.compileTime = configuration.getString("git.build.time");
    }

    public boolean isSnapshotOrBeta() {
        return "master".equals(branch) || "dev".equals(branch);
    }

    public String toString() {
        return "BuildMeta(version=" + this.getVersion() + ")";
    }
}
