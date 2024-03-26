package com.ghostchu.peerbanhelper.wrapper;

import java.util.UUID;

public class BanMetadata implements Comparable<BanMetadata> {
    private UUID randomId = UUID.randomUUID();
    private long banAt;
    private long unbanAt;
    private String description;

    public BanMetadata(UUID randomId, long banAt, long unbanAt, String description) {
        this.randomId = randomId;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
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

    public void setDescription(String description) {
        this.description = description;
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
        final Object $description = this.getDescription();
        result = result * PRIME + ($description == null ? 43 : $description.hashCode());
        return result;
    }

    public String toString() {
        return "BanMetadata(randomId=" + this.getRandomId() + ", banAt=" + this.getBanAt() + ", unbanAt=" + this.getUnbanAt() + ", description=" + this.getDescription() + ")";
    }
}
