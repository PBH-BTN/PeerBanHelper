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
    private String compileEmail;

    public void loadBuildMeta(Properties configuration) {
        this.version = ExternalSwitch.parse("pbh.buildmeta.maven.version", configuration.getProperty("git.build.version", "0.0.0-undefined"));
        this.branch = ExternalSwitch.parse("pbh.buildmeta.git.branch", configuration.getProperty("git.branch", "Unknown"));
        this.commit = configuration.getProperty("git.commit.id", "Unknown");
        this.abbrev = configuration.getProperty("git.commit.id.abbrev", "Unknown");
        this.os = System.getProperty("os.name", "Unknown");
        this.compileTime = configuration.getProperty("git.commit.time", "Unknown");
        this.compileHost = configuration.getProperty("git.build.host", "Unknown");
        this.compileUser = configuration.getProperty("git.commit.user.name", "Unknown");
        this.compileEmail = configuration.getProperty("git.commit.user.email", "Unknown");
    }

    public boolean isSnapshotOrBeta() {
        return !"release".equalsIgnoreCase(branch);
    }

    public String toString() {
        return "BuildMeta(version=" + this.getVersion() + ")";
    }
}
