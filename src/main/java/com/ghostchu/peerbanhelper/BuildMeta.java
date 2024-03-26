package com.ghostchu.peerbanhelper;

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

public class BuildMeta {
    private String version = "unknown";

    public BuildMeta() {
    }

    public void loadBuildMeta(YamlConfiguration configuration) {
        this.version = configuration.getString("maven.version");
    }

    public String getVersion() {
        return this.version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof BuildMeta)) return false;
        final BuildMeta other = (BuildMeta) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$version = this.getVersion();
        final Object other$version = other.getVersion();
        if (this$version == null ? other$version != null : !this$version.equals(other$version)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof BuildMeta;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $version = this.getVersion();
        result = result * PRIME + ($version == null ? 43 : $version.hashCode());
        return result;
    }

    public String toString() {
        return "BuildMeta(version=" + this.getVersion() + ")";
    }
}
