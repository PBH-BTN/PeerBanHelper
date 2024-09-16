package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;

import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class QBittorrentTorrent implements Torrent {
    @SerializedName("added_on")
    private Long addedOn;

    @SerializedName("amount_left")
    private Long amountLeft;

    @SerializedName("auto_tmm")
    private Boolean autoTmm;

    @SerializedName("availability")
    private Double availability;

    @SerializedName("category")
    private String category;

    @SerializedName("completed")
    private Long completed;

    @SerializedName("completion_on")
    private Long completionOn;

    @SerializedName("content_path")
    private String contentPath;

    @SerializedName("dl_limit")
    private Long dlLimit;

    @SerializedName("dlspeed")
    private Long dlspeed;

    @SerializedName("download_path")
    private String downloadPath;

    @SerializedName("downloaded")
    private Long downloaded;

    @SerializedName("downloaded_session")
    private Long downloadedSession;

    @SerializedName("eta")
    private Long eta;
    @SerializedName("f_l_piece_prio")
    private Boolean fLPiecePrio;

    @SerializedName("force_start")
    private Boolean forceStart;

    @SerializedName("hash")
    private String hash;

    @SerializedName("inactive_seeding_time_limit")
    private Long inactiveSeedingTimeLimit;

    @SerializedName("infohash_v1")
    private String infohashV1;

    @SerializedName("infohash_v2")
    private String infohashV2;

    @SerializedName("last_activity")
    private Long lastActivity;

    @SerializedName("magnet_uri")
    private String magnetUri;

    @SerializedName("max_inactive_seeding_time")
    private Long maxInactiveSeedingTime;

    @SerializedName("max_ratio")
    private Double maxRatio;

    @SerializedName("max_seeding_time")
    private Long maxSeedingTime;

    @SerializedName("name")
    private String name;

    @SerializedName("num_complete")
    private Long numComplete;

    @SerializedName("num_incomplete")
    private Long numIncomplete;

    @SerializedName("num_leechs")
    private Long numLeechs;

    @SerializedName("num_seeds")
    private Long numSeeds;

    @SerializedName("priority")
    private Long priority;

    @SerializedName("progress")
    private Double progress;

    @SerializedName("ratio")
    private Double ratio;

    @SerializedName("ratio_limit")
    private Double ratioLimit;

    @SerializedName("save_path")
    private String savePath;

    @SerializedName("seeding_time")
    private Long seedingTime;

    @SerializedName("seeding_time_limit")
    private Long seedingTimeLimit;

    @SerializedName("seen_complete")
    private Long seenComplete;

    @SerializedName("seq_dl")
    private Boolean seqDl;

    @SerializedName("size")
    private Long size;

    @SerializedName("state")
    private String state;

    @SerializedName("super_seeding")
    private Boolean superSeeding;

    @SerializedName("tags")
    private String tags;

    @SerializedName("time_active")
    private Long timeActive;

    @SerializedName("total_size")
    private Long totalSize;

    @SerializedName("tracker")
    private String tracker;

    @SerializedName("trackers_count")
    private Long trackersCount;

    @SerializedName("up_limit")
    private Long upLimit;

    @SerializedName("uploaded")
    private Long uploaded;

    @SerializedName("uploaded_session")
    private Long uploadedSession;

    @SerializedName("upspeed")
    private Long upspeed;

    @SerializedName("is_private")
    private Boolean privateTorrent;

    @Override
    public String getId() {
        return hash;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getRtUploadSpeed() {
        return upspeed;
    }

    @Override
    public long getRtDownloadSpeed() {
        return dlspeed;
    }

    @Override
    public boolean isPrivate() {
        return privateTorrent != null && privateTorrent;
    }

    @Override
    public String getHashedIdentifier() {
        return Torrent.super.getHashedIdentifier();
    }
}
