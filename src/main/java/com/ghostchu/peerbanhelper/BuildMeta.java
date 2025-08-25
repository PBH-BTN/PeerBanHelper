package com.ghostchu.peerbanhelper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Properties;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class BuildMeta {
    private String version = "999.999.999-undefined";
    private String os = "Unknown";
    private String branch = "Unknown";
    private String commit = "Unknown";
    private String abbrev = "Unknown";
    private String compileTime = "Unknown";
    private String compileHost;
    private String compileUser;

    public void loadBuildMeta(Properties configuration) {
        this.version = ExternalSwitch.parse("pbh.buildmeta.maven.version", configuration.getProperty("git.build.version"));
        this.branch = ExternalSwitch.parse("pbh.buildmeta.git.branch", configuration.getProperty("git.branch"));
        this.commit = configuration.getProperty("git.commit.id.full");
        this.abbrev = configuration.getProperty("git.commit.id.abbrev");
        this.os = System.getProperty("os.name");
        this.compileTime = configuration.getProperty("git.build.time", "Unknown");
        this.compileHost = configuration.getProperty("git.build.host", "Unknown");
        this.compileUser = configuration.getProperty("git.build.user.name", "Unknown");
    }

    public boolean isSnapshotOrBeta() {
        return "master".equals(branch) || "dev".equals(branch) || "LiveDebug".equalsIgnoreCase(ExternalSwitch.parse("pbh.release"));
    }

    public String toString() {
        return "BuildMeta(version=" + this.getVersion() + ")";
    }
}
