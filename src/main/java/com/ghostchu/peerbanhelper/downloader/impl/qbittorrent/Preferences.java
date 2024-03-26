package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.google.gson.annotations.SerializedName;

public class Preferences {

    @SerializedName("banned_IPs")
    private String bannedIps;

    public Preferences() {
    }

    public String getBannedIps() {
        return this.bannedIps;
    }

    public void setBannedIps(String bannedIps) {
        this.bannedIps = bannedIps;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Preferences)) return false;
        final Preferences other = (Preferences) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$bannedIps = this.getBannedIps();
        final Object other$bannedIps = other.getBannedIps();
        if (this$bannedIps == null ? other$bannedIps != null : !this$bannedIps.equals(other$bannedIps)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Preferences;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $bannedIps = this.getBannedIps();
        result = result * PRIME + ($bannedIps == null ? 43 : $bannedIps.hashCode());
        return result;
    }

    public String toString() {
        return "Preferences(bannedIps=" + this.getBannedIps() + ")";
    }
}
