package com.ghostchu.peerbanhelper.wrapper;

import java.util.UUID;

public class BanMetadata implements Comparable<BanMetadata> {
    private UUID randomId = UUID.randomUUID();
    private long banAt;
    private long unbanAt;
    private String torrent;
    private String torrentId;
    private String torrentHash;
    private String description;

    public BanMetadata(UUID randomId, long banAt, long unbanAt, String torrentId, String torrent, String torrentHash, String description) {
        this.randomId = randomId;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.torrent = torrent;
        this.torrentId = torrentId;
        this.torrentHash = torrentHash;
        this.description = description;
    }

    public BanMetadata() {
    }

    @Override
    public int compareTo(BanMetadata o) {
        return this.randomId.compareTo(o.randomId);
    }

    public UUID getRandomId() {
        return this.randomId;
    }

    public long getBanAt() {
        return this.banAt;
    }

    public long getUnbanAt() {
        return this.unbanAt;
    }

    public String getDescription() {
        return this.description;
    }

    public void setRandomId(UUID randomId) {
        this.randomId = randomId;
    }

    public void setBanAt(long banAt) {
        this.banAt = banAt;
    }

    public void setUnbanAt(long unbanAt) {
        this.unbanAt = unbanAt;
    }

    public String getTorrent() {
        return torrent;
    }

    public void setTorrent(String torrent) {
        this.torrent = torrent;
    }

    public String getTorrentHash() {
        return torrentHash;
    }

    public void setTorrentHash(String torrentHash) {
        this.torrentHash = torrentHash;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTorrentId() {
        return torrentId;
    }

    public void setTorrentId(String torrentId) {
        this.torrentId = torrentId;
    }


    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof BanMetadata)) return false;
        final BanMetadata other = (BanMetadata) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$randomId = this.getRandomId();
        final Object other$randomId = other.getRandomId();
        if (this$randomId == null ? other$randomId != null : !this$randomId.equals(other$randomId)) return false;
        if (this.getBanAt() != other.getBanAt()) return false;
        if (this.getUnbanAt() != other.getUnbanAt()) return false;
        final Object this$torrent = this.getTorrent();
        final Object other$torrent = other.getTorrent();
        if (this$torrent == null ? other$torrent != null : !this$torrent.equals(other$torrent)) return false;
        final Object this$torrentId = this.getTorrentId();
        final Object other$torrentId = other.getTorrentId();
        if (this$torrentId == null ? other$torrentId != null : !this$torrentId.equals(other$torrentId)) return false;
        final Object this$torrentHash = this.getTorrentHash();
        final Object other$torrentHash = other.getTorrentHash();
        if (this$torrentHash == null ? other$torrentHash != null : !this$torrentHash.equals(other$torrentHash))
            return false;
        final Object this$description = this.getDescription();
        final Object other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof BanMetadata;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $randomId = this.getRandomId();
        result = result * PRIME + ($randomId == null ? 43 : $randomId.hashCode());
        final long $banAt = this.getBanAt();
        result = result * PRIME + (int) ($banAt >>> 32 ^ $banAt);
        final long $unbanAt = this.getUnbanAt();
        result = result * PRIME + (int) ($unbanAt >>> 32 ^ $unbanAt);
        final Object $torrent = this.getTorrent();
        result = result * PRIME + ($torrent == null ? 43 : $torrent.hashCode());
        final Object $torrentId = this.getTorrentId();
        result = result * PRIME + ($torrentId == null ? 43 : $torrentId.hashCode());
        final Object $torrentHash = this.getTorrentHash();
        result = result * PRIME + ($torrentHash == null ? 43 : $torrentHash.hashCode());
        final Object $description = this.getDescription();
        result = result * PRIME + ($description == null ? 43 : $description.hashCode());
        return result;
    }

    public String toString() {
        return "BanMetadata(randomId=" + this.getRandomId() + ", banAt=" + this.getBanAt() + ", unbanAt=" + this.getUnbanAt() + ", torrent=" + this.getTorrent() + ", torrentId=" + this.getTorrentId() + ", torrentHash=" + this.getTorrentHash() + ", description=" + this.getDescription() + ")";
    }
}
