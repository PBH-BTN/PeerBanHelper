package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.google.gson.annotations.SerializedName;

public class TorrentDetail {

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

    public TorrentDetail() {
    }

    public Long getAddedOn() {
        return this.addedOn;
    }

    public Long getAmountLeft() {
        return this.amountLeft;
    }

    public Boolean getAutoTmm() {
        return this.autoTmm;
    }

    public Double getAvailability() {
        return this.availability;
    }

    public String getCategory() {
        return this.category;
    }

    public Long getCompleted() {
        return this.completed;
    }

    public Long getCompletionOn() {
        return this.completionOn;
    }

    public String getContentPath() {
        return this.contentPath;
    }

    public Long getDlLimit() {
        return this.dlLimit;
    }

    public Long getDlspeed() {
        return this.dlspeed;
    }

    public String getDownloadPath() {
        return this.downloadPath;
    }

    public Long getDownloaded() {
        return this.downloaded;
    }

    public Long getDownloadedSession() {
        return this.downloadedSession;
    }

    public Long getEta() {
        return this.eta;
    }

    public Boolean getFLPiecePrio() {
        return this.fLPiecePrio;
    }

    public Boolean getForceStart() {
        return this.forceStart;
    }

    public String getHash() {
        return this.hash;
    }

    public Long getInactiveSeedingTimeLimit() {
        return this.inactiveSeedingTimeLimit;
    }

    public String getInfohashV1() {
        return this.infohashV1;
    }

    public String getInfohashV2() {
        return this.infohashV2;
    }

    public Long getLastActivity() {
        return this.lastActivity;
    }

    public String getMagnetUri() {
        return this.magnetUri;
    }

    public Long getMaxInactiveSeedingTime() {
        return this.maxInactiveSeedingTime;
    }

    public Double getMaxRatio() {
        return this.maxRatio;
    }

    public Long getMaxSeedingTime() {
        return this.maxSeedingTime;
    }

    public String getName() {
        return this.name;
    }

    public Long getNumComplete() {
        return this.numComplete;
    }

    public Long getNumIncomplete() {
        return this.numIncomplete;
    }

    public Long getNumLeechs() {
        return this.numLeechs;
    }

    public Long getNumSeeds() {
        return this.numSeeds;
    }

    public Long getPriority() {
        return this.priority;
    }

    public Double getProgress() {
        return this.progress;
    }

    public Double getRatio() {
        return this.ratio;
    }

    public Double getRatioLimit() {
        return this.ratioLimit;
    }

    public String getSavePath() {
        return this.savePath;
    }

    public Long getSeedingTime() {
        return this.seedingTime;
    }

    public Long getSeedingTimeLimit() {
        return this.seedingTimeLimit;
    }

    public Long getSeenComplete() {
        return this.seenComplete;
    }

    public Boolean getSeqDl() {
        return this.seqDl;
    }

    public Long getSize() {
        return this.size;
    }

    public String getState() {
        return this.state;
    }

    public Boolean getSuperSeeding() {
        return this.superSeeding;
    }

    public String getTags() {
        return this.tags;
    }

    public Long getTimeActive() {
        return this.timeActive;
    }

    public Long getTotalSize() {
        return this.totalSize;
    }

    public String getTracker() {
        return this.tracker;
    }

    public Long getTrackersCount() {
        return this.trackersCount;
    }

    public Long getUpLimit() {
        return this.upLimit;
    }

    public Long getUploaded() {
        return this.uploaded;
    }

    public Long getUploadedSession() {
        return this.uploadedSession;
    }

    public Long getUpspeed() {
        return this.upspeed;
    }

    public void setAddedOn(Long addedOn) {
        this.addedOn = addedOn;
    }

    public void setAmountLeft(Long amountLeft) {
        this.amountLeft = amountLeft;
    }

    public void setAutoTmm(Boolean autoTmm) {
        this.autoTmm = autoTmm;
    }

    public void setAvailability(Double availability) {
        this.availability = availability;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setCompleted(Long completed) {
        this.completed = completed;
    }

    public void setCompletionOn(Long completionOn) {
        this.completionOn = completionOn;
    }

    public void setContentPath(String contentPath) {
        this.contentPath = contentPath;
    }

    public void setDlLimit(Long dlLimit) {
        this.dlLimit = dlLimit;
    }

    public void setDlspeed(Long dlspeed) {
        this.dlspeed = dlspeed;
    }

    public void setDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public void setDownloaded(Long downloaded) {
        this.downloaded = downloaded;
    }

    public void setDownloadedSession(Long downloadedSession) {
        this.downloadedSession = downloadedSession;
    }

    public void setEta(Long eta) {
        this.eta = eta;
    }

    public void setFLPiecePrio(Boolean fLPiecePrio) {
        this.fLPiecePrio = fLPiecePrio;
    }

    public void setForceStart(Boolean forceStart) {
        this.forceStart = forceStart;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setInactiveSeedingTimeLimit(Long inactiveSeedingTimeLimit) {
        this.inactiveSeedingTimeLimit = inactiveSeedingTimeLimit;
    }

    public void setInfohashV1(String infohashV1) {
        this.infohashV1 = infohashV1;
    }

    public void setInfohashV2(String infohashV2) {
        this.infohashV2 = infohashV2;
    }

    public void setLastActivity(Long lastActivity) {
        this.lastActivity = lastActivity;
    }

    public void setMagnetUri(String magnetUri) {
        this.magnetUri = magnetUri;
    }

    public void setMaxInactiveSeedingTime(Long maxInactiveSeedingTime) {
        this.maxInactiveSeedingTime = maxInactiveSeedingTime;
    }

    public void setMaxRatio(Double maxRatio) {
        this.maxRatio = maxRatio;
    }

    public void setMaxSeedingTime(Long maxSeedingTime) {
        this.maxSeedingTime = maxSeedingTime;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNumComplete(Long numComplete) {
        this.numComplete = numComplete;
    }

    public void setNumIncomplete(Long numIncomplete) {
        this.numIncomplete = numIncomplete;
    }

    public void setNumLeechs(Long numLeechs) {
        this.numLeechs = numLeechs;
    }

    public void setNumSeeds(Long numSeeds) {
        this.numSeeds = numSeeds;
    }

    public void setPriority(Long priority) {
        this.priority = priority;
    }

    public void setProgress(Double progress) {
        this.progress = progress;
    }

    public void setRatio(Double ratio) {
        this.ratio = ratio;
    }

    public void setRatioLimit(Double ratioLimit) {
        this.ratioLimit = ratioLimit;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public void setSeedingTime(Long seedingTime) {
        this.seedingTime = seedingTime;
    }

    public void setSeedingTimeLimit(Long seedingTimeLimit) {
        this.seedingTimeLimit = seedingTimeLimit;
    }

    public void setSeenComplete(Long seenComplete) {
        this.seenComplete = seenComplete;
    }

    public void setSeqDl(Boolean seqDl) {
        this.seqDl = seqDl;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setSuperSeeding(Boolean superSeeding) {
        this.superSeeding = superSeeding;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setTimeActive(Long timeActive) {
        this.timeActive = timeActive;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public void setTracker(String tracker) {
        this.tracker = tracker;
    }

    public void setTrackersCount(Long trackersCount) {
        this.trackersCount = trackersCount;
    }

    public void setUpLimit(Long upLimit) {
        this.upLimit = upLimit;
    }

    public void setUploaded(Long uploaded) {
        this.uploaded = uploaded;
    }

    public void setUploadedSession(Long uploadedSession) {
        this.uploadedSession = uploadedSession;
    }

    public void setUpspeed(Long upspeed) {
        this.upspeed = upspeed;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof TorrentDetail)) return false;
        final TorrentDetail other = (TorrentDetail) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$addedOn = this.getAddedOn();
        final Object other$addedOn = other.getAddedOn();
        if (this$addedOn == null ? other$addedOn != null : !this$addedOn.equals(other$addedOn)) return false;
        final Object this$amountLeft = this.getAmountLeft();
        final Object other$amountLeft = other.getAmountLeft();
        if (this$amountLeft == null ? other$amountLeft != null : !this$amountLeft.equals(other$amountLeft))
            return false;
        final Object this$autoTmm = this.getAutoTmm();
        final Object other$autoTmm = other.getAutoTmm();
        if (this$autoTmm == null ? other$autoTmm != null : !this$autoTmm.equals(other$autoTmm)) return false;
        final Object this$availability = this.getAvailability();
        final Object other$availability = other.getAvailability();
        if (this$availability == null ? other$availability != null : !this$availability.equals(other$availability))
            return false;
        final Object this$category = this.getCategory();
        final Object other$category = other.getCategory();
        if (this$category == null ? other$category != null : !this$category.equals(other$category)) return false;
        final Object this$completed = this.getCompleted();
        final Object other$completed = other.getCompleted();
        if (this$completed == null ? other$completed != null : !this$completed.equals(other$completed)) return false;
        final Object this$completionOn = this.getCompletionOn();
        final Object other$completionOn = other.getCompletionOn();
        if (this$completionOn == null ? other$completionOn != null : !this$completionOn.equals(other$completionOn))
            return false;
        final Object this$contentPath = this.getContentPath();
        final Object other$contentPath = other.getContentPath();
        if (this$contentPath == null ? other$contentPath != null : !this$contentPath.equals(other$contentPath))
            return false;
        final Object this$dlLimit = this.getDlLimit();
        final Object other$dlLimit = other.getDlLimit();
        if (this$dlLimit == null ? other$dlLimit != null : !this$dlLimit.equals(other$dlLimit)) return false;
        final Object this$dlspeed = this.getDlspeed();
        final Object other$dlspeed = other.getDlspeed();
        if (this$dlspeed == null ? other$dlspeed != null : !this$dlspeed.equals(other$dlspeed)) return false;
        final Object this$downloadPath = this.getDownloadPath();
        final Object other$downloadPath = other.getDownloadPath();
        if (this$downloadPath == null ? other$downloadPath != null : !this$downloadPath.equals(other$downloadPath))
            return false;
        final Object this$downloaded = this.getDownloaded();
        final Object other$downloaded = other.getDownloaded();
        if (this$downloaded == null ? other$downloaded != null : !this$downloaded.equals(other$downloaded))
            return false;
        final Object this$downloadedSession = this.getDownloadedSession();
        final Object other$downloadedSession = other.getDownloadedSession();
        if (this$downloadedSession == null ? other$downloadedSession != null : !this$downloadedSession.equals(other$downloadedSession))
            return false;
        final Object this$eta = this.getEta();
        final Object other$eta = other.getEta();
        if (this$eta == null ? other$eta != null : !this$eta.equals(other$eta)) return false;
        final Object this$fLPiecePrio = this.getFLPiecePrio();
        final Object other$fLPiecePrio = other.getFLPiecePrio();
        if (this$fLPiecePrio == null ? other$fLPiecePrio != null : !this$fLPiecePrio.equals(other$fLPiecePrio))
            return false;
        final Object this$forceStart = this.getForceStart();
        final Object other$forceStart = other.getForceStart();
        if (this$forceStart == null ? other$forceStart != null : !this$forceStart.equals(other$forceStart))
            return false;
        final Object this$hash = this.getHash();
        final Object other$hash = other.getHash();
        if (this$hash == null ? other$hash != null : !this$hash.equals(other$hash)) return false;
        final Object this$inactiveSeedingTimeLimit = this.getInactiveSeedingTimeLimit();
        final Object other$inactiveSeedingTimeLimit = other.getInactiveSeedingTimeLimit();
        if (this$inactiveSeedingTimeLimit == null ? other$inactiveSeedingTimeLimit != null : !this$inactiveSeedingTimeLimit.equals(other$inactiveSeedingTimeLimit))
            return false;
        final Object this$infohashV1 = this.getInfohashV1();
        final Object other$infohashV1 = other.getInfohashV1();
        if (this$infohashV1 == null ? other$infohashV1 != null : !this$infohashV1.equals(other$infohashV1))
            return false;
        final Object this$infohashV2 = this.getInfohashV2();
        final Object other$infohashV2 = other.getInfohashV2();
        if (this$infohashV2 == null ? other$infohashV2 != null : !this$infohashV2.equals(other$infohashV2))
            return false;
        final Object this$lastActivity = this.getLastActivity();
        final Object other$lastActivity = other.getLastActivity();
        if (this$lastActivity == null ? other$lastActivity != null : !this$lastActivity.equals(other$lastActivity))
            return false;
        final Object this$magnetUri = this.getMagnetUri();
        final Object other$magnetUri = other.getMagnetUri();
        if (this$magnetUri == null ? other$magnetUri != null : !this$magnetUri.equals(other$magnetUri)) return false;
        final Object this$maxInactiveSeedingTime = this.getMaxInactiveSeedingTime();
        final Object other$maxInactiveSeedingTime = other.getMaxInactiveSeedingTime();
        if (this$maxInactiveSeedingTime == null ? other$maxInactiveSeedingTime != null : !this$maxInactiveSeedingTime.equals(other$maxInactiveSeedingTime))
            return false;
        final Object this$maxRatio = this.getMaxRatio();
        final Object other$maxRatio = other.getMaxRatio();
        if (this$maxRatio == null ? other$maxRatio != null : !this$maxRatio.equals(other$maxRatio)) return false;
        final Object this$maxSeedingTime = this.getMaxSeedingTime();
        final Object other$maxSeedingTime = other.getMaxSeedingTime();
        if (this$maxSeedingTime == null ? other$maxSeedingTime != null : !this$maxSeedingTime.equals(other$maxSeedingTime))
            return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$numComplete = this.getNumComplete();
        final Object other$numComplete = other.getNumComplete();
        if (this$numComplete == null ? other$numComplete != null : !this$numComplete.equals(other$numComplete))
            return false;
        final Object this$numIncomplete = this.getNumIncomplete();
        final Object other$numIncomplete = other.getNumIncomplete();
        if (this$numIncomplete == null ? other$numIncomplete != null : !this$numIncomplete.equals(other$numIncomplete))
            return false;
        final Object this$numLeechs = this.getNumLeechs();
        final Object other$numLeechs = other.getNumLeechs();
        if (this$numLeechs == null ? other$numLeechs != null : !this$numLeechs.equals(other$numLeechs)) return false;
        final Object this$numSeeds = this.getNumSeeds();
        final Object other$numSeeds = other.getNumSeeds();
        if (this$numSeeds == null ? other$numSeeds != null : !this$numSeeds.equals(other$numSeeds)) return false;
        final Object this$priority = this.getPriority();
        final Object other$priority = other.getPriority();
        if (this$priority == null ? other$priority != null : !this$priority.equals(other$priority)) return false;
        final Object this$progress = this.getProgress();
        final Object other$progress = other.getProgress();
        if (this$progress == null ? other$progress != null : !this$progress.equals(other$progress)) return false;
        final Object this$ratio = this.getRatio();
        final Object other$ratio = other.getRatio();
        if (this$ratio == null ? other$ratio != null : !this$ratio.equals(other$ratio)) return false;
        final Object this$ratioLimit = this.getRatioLimit();
        final Object other$ratioLimit = other.getRatioLimit();
        if (this$ratioLimit == null ? other$ratioLimit != null : !this$ratioLimit.equals(other$ratioLimit))
            return false;
        final Object this$savePath = this.getSavePath();
        final Object other$savePath = other.getSavePath();
        if (this$savePath == null ? other$savePath != null : !this$savePath.equals(other$savePath)) return false;
        final Object this$seedingTime = this.getSeedingTime();
        final Object other$seedingTime = other.getSeedingTime();
        if (this$seedingTime == null ? other$seedingTime != null : !this$seedingTime.equals(other$seedingTime))
            return false;
        final Object this$seedingTimeLimit = this.getSeedingTimeLimit();
        final Object other$seedingTimeLimit = other.getSeedingTimeLimit();
        if (this$seedingTimeLimit == null ? other$seedingTimeLimit != null : !this$seedingTimeLimit.equals(other$seedingTimeLimit))
            return false;
        final Object this$seenComplete = this.getSeenComplete();
        final Object other$seenComplete = other.getSeenComplete();
        if (this$seenComplete == null ? other$seenComplete != null : !this$seenComplete.equals(other$seenComplete))
            return false;
        final Object this$seqDl = this.getSeqDl();
        final Object other$seqDl = other.getSeqDl();
        if (this$seqDl == null ? other$seqDl != null : !this$seqDl.equals(other$seqDl)) return false;
        final Object this$size = this.getSize();
        final Object other$size = other.getSize();
        if (this$size == null ? other$size != null : !this$size.equals(other$size)) return false;
        final Object this$state = this.getState();
        final Object other$state = other.getState();
        if (this$state == null ? other$state != null : !this$state.equals(other$state)) return false;
        final Object this$superSeeding = this.getSuperSeeding();
        final Object other$superSeeding = other.getSuperSeeding();
        if (this$superSeeding == null ? other$superSeeding != null : !this$superSeeding.equals(other$superSeeding))
            return false;
        final Object this$tags = this.getTags();
        final Object other$tags = other.getTags();
        if (this$tags == null ? other$tags != null : !this$tags.equals(other$tags)) return false;
        final Object this$timeActive = this.getTimeActive();
        final Object other$timeActive = other.getTimeActive();
        if (this$timeActive == null ? other$timeActive != null : !this$timeActive.equals(other$timeActive))
            return false;
        final Object this$totalSize = this.getTotalSize();
        final Object other$totalSize = other.getTotalSize();
        if (this$totalSize == null ? other$totalSize != null : !this$totalSize.equals(other$totalSize)) return false;
        final Object this$tracker = this.getTracker();
        final Object other$tracker = other.getTracker();
        if (this$tracker == null ? other$tracker != null : !this$tracker.equals(other$tracker)) return false;
        final Object this$trackersCount = this.getTrackersCount();
        final Object other$trackersCount = other.getTrackersCount();
        if (this$trackersCount == null ? other$trackersCount != null : !this$trackersCount.equals(other$trackersCount))
            return false;
        final Object this$upLimit = this.getUpLimit();
        final Object other$upLimit = other.getUpLimit();
        if (this$upLimit == null ? other$upLimit != null : !this$upLimit.equals(other$upLimit)) return false;
        final Object this$uploaded = this.getUploaded();
        final Object other$uploaded = other.getUploaded();
        if (this$uploaded == null ? other$uploaded != null : !this$uploaded.equals(other$uploaded)) return false;
        final Object this$uploadedSession = this.getUploadedSession();
        final Object other$uploadedSession = other.getUploadedSession();
        if (this$uploadedSession == null ? other$uploadedSession != null : !this$uploadedSession.equals(other$uploadedSession))
            return false;
        final Object this$upspeed = this.getUpspeed();
        final Object other$upspeed = other.getUpspeed();
        if (this$upspeed == null ? other$upspeed != null : !this$upspeed.equals(other$upspeed)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof TorrentDetail;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $addedOn = this.getAddedOn();
        result = result * PRIME + ($addedOn == null ? 43 : $addedOn.hashCode());
        final Object $amountLeft = this.getAmountLeft();
        result = result * PRIME + ($amountLeft == null ? 43 : $amountLeft.hashCode());
        final Object $autoTmm = this.getAutoTmm();
        result = result * PRIME + ($autoTmm == null ? 43 : $autoTmm.hashCode());
        final Object $availability = this.getAvailability();
        result = result * PRIME + ($availability == null ? 43 : $availability.hashCode());
        final Object $category = this.getCategory();
        result = result * PRIME + ($category == null ? 43 : $category.hashCode());
        final Object $completed = this.getCompleted();
        result = result * PRIME + ($completed == null ? 43 : $completed.hashCode());
        final Object $completionOn = this.getCompletionOn();
        result = result * PRIME + ($completionOn == null ? 43 : $completionOn.hashCode());
        final Object $contentPath = this.getContentPath();
        result = result * PRIME + ($contentPath == null ? 43 : $contentPath.hashCode());
        final Object $dlLimit = this.getDlLimit();
        result = result * PRIME + ($dlLimit == null ? 43 : $dlLimit.hashCode());
        final Object $dlspeed = this.getDlspeed();
        result = result * PRIME + ($dlspeed == null ? 43 : $dlspeed.hashCode());
        final Object $downloadPath = this.getDownloadPath();
        result = result * PRIME + ($downloadPath == null ? 43 : $downloadPath.hashCode());
        final Object $downloaded = this.getDownloaded();
        result = result * PRIME + ($downloaded == null ? 43 : $downloaded.hashCode());
        final Object $downloadedSession = this.getDownloadedSession();
        result = result * PRIME + ($downloadedSession == null ? 43 : $downloadedSession.hashCode());
        final Object $eta = this.getEta();
        result = result * PRIME + ($eta == null ? 43 : $eta.hashCode());
        final Object $fLPiecePrio = this.getFLPiecePrio();
        result = result * PRIME + ($fLPiecePrio == null ? 43 : $fLPiecePrio.hashCode());
        final Object $forceStart = this.getForceStart();
        result = result * PRIME + ($forceStart == null ? 43 : $forceStart.hashCode());
        final Object $hash = this.getHash();
        result = result * PRIME + ($hash == null ? 43 : $hash.hashCode());
        final Object $inactiveSeedingTimeLimit = this.getInactiveSeedingTimeLimit();
        result = result * PRIME + ($inactiveSeedingTimeLimit == null ? 43 : $inactiveSeedingTimeLimit.hashCode());
        final Object $infohashV1 = this.getInfohashV1();
        result = result * PRIME + ($infohashV1 == null ? 43 : $infohashV1.hashCode());
        final Object $infohashV2 = this.getInfohashV2();
        result = result * PRIME + ($infohashV2 == null ? 43 : $infohashV2.hashCode());
        final Object $lastActivity = this.getLastActivity();
        result = result * PRIME + ($lastActivity == null ? 43 : $lastActivity.hashCode());
        final Object $magnetUri = this.getMagnetUri();
        result = result * PRIME + ($magnetUri == null ? 43 : $magnetUri.hashCode());
        final Object $maxInactiveSeedingTime = this.getMaxInactiveSeedingTime();
        result = result * PRIME + ($maxInactiveSeedingTime == null ? 43 : $maxInactiveSeedingTime.hashCode());
        final Object $maxRatio = this.getMaxRatio();
        result = result * PRIME + ($maxRatio == null ? 43 : $maxRatio.hashCode());
        final Object $maxSeedingTime = this.getMaxSeedingTime();
        result = result * PRIME + ($maxSeedingTime == null ? 43 : $maxSeedingTime.hashCode());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $numComplete = this.getNumComplete();
        result = result * PRIME + ($numComplete == null ? 43 : $numComplete.hashCode());
        final Object $numIncomplete = this.getNumIncomplete();
        result = result * PRIME + ($numIncomplete == null ? 43 : $numIncomplete.hashCode());
        final Object $numLeechs = this.getNumLeechs();
        result = result * PRIME + ($numLeechs == null ? 43 : $numLeechs.hashCode());
        final Object $numSeeds = this.getNumSeeds();
        result = result * PRIME + ($numSeeds == null ? 43 : $numSeeds.hashCode());
        final Object $priority = this.getPriority();
        result = result * PRIME + ($priority == null ? 43 : $priority.hashCode());
        final Object $progress = this.getProgress();
        result = result * PRIME + ($progress == null ? 43 : $progress.hashCode());
        final Object $ratio = this.getRatio();
        result = result * PRIME + ($ratio == null ? 43 : $ratio.hashCode());
        final Object $ratioLimit = this.getRatioLimit();
        result = result * PRIME + ($ratioLimit == null ? 43 : $ratioLimit.hashCode());
        final Object $savePath = this.getSavePath();
        result = result * PRIME + ($savePath == null ? 43 : $savePath.hashCode());
        final Object $seedingTime = this.getSeedingTime();
        result = result * PRIME + ($seedingTime == null ? 43 : $seedingTime.hashCode());
        final Object $seedingTimeLimit = this.getSeedingTimeLimit();
        result = result * PRIME + ($seedingTimeLimit == null ? 43 : $seedingTimeLimit.hashCode());
        final Object $seenComplete = this.getSeenComplete();
        result = result * PRIME + ($seenComplete == null ? 43 : $seenComplete.hashCode());
        final Object $seqDl = this.getSeqDl();
        result = result * PRIME + ($seqDl == null ? 43 : $seqDl.hashCode());
        final Object $size = this.getSize();
        result = result * PRIME + ($size == null ? 43 : $size.hashCode());
        final Object $state = this.getState();
        result = result * PRIME + ($state == null ? 43 : $state.hashCode());
        final Object $superSeeding = this.getSuperSeeding();
        result = result * PRIME + ($superSeeding == null ? 43 : $superSeeding.hashCode());
        final Object $tags = this.getTags();
        result = result * PRIME + ($tags == null ? 43 : $tags.hashCode());
        final Object $timeActive = this.getTimeActive();
        result = result * PRIME + ($timeActive == null ? 43 : $timeActive.hashCode());
        final Object $totalSize = this.getTotalSize();
        result = result * PRIME + ($totalSize == null ? 43 : $totalSize.hashCode());
        final Object $tracker = this.getTracker();
        result = result * PRIME + ($tracker == null ? 43 : $tracker.hashCode());
        final Object $trackersCount = this.getTrackersCount();
        result = result * PRIME + ($trackersCount == null ? 43 : $trackersCount.hashCode());
        final Object $upLimit = this.getUpLimit();
        result = result * PRIME + ($upLimit == null ? 43 : $upLimit.hashCode());
        final Object $uploaded = this.getUploaded();
        result = result * PRIME + ($uploaded == null ? 43 : $uploaded.hashCode());
        final Object $uploadedSession = this.getUploadedSession();
        result = result * PRIME + ($uploadedSession == null ? 43 : $uploadedSession.hashCode());
        final Object $upspeed = this.getUpspeed();
        result = result * PRIME + ($upspeed == null ? 43 : $upspeed.hashCode());
        return result;
    }

    public String toString() {
        return "TorrentDetail(addedOn=" + this.getAddedOn() + ", amountLeft=" + this.getAmountLeft() + ", autoTmm=" + this.getAutoTmm() + ", availability=" + this.getAvailability() + ", category=" + this.getCategory() + ", completed=" + this.getCompleted() + ", completionOn=" + this.getCompletionOn() + ", contentPath=" + this.getContentPath() + ", dlLimit=" + this.getDlLimit() + ", dlspeed=" + this.getDlspeed() + ", downloadPath=" + this.getDownloadPath() + ", downloaded=" + this.getDownloaded() + ", downloadedSession=" + this.getDownloadedSession() + ", eta=" + this.getEta() + ", fLPiecePrio=" + this.getFLPiecePrio() + ", forceStart=" + this.getForceStart() + ", hash=" + this.getHash() + ", inactiveSeedingTimeLimit=" + this.getInactiveSeedingTimeLimit() + ", infohashV1=" + this.getInfohashV1() + ", infohashV2=" + this.getInfohashV2() + ", lastActivity=" + this.getLastActivity() + ", magnetUri=" + this.getMagnetUri() + ", maxInactiveSeedingTime=" + this.getMaxInactiveSeedingTime() + ", maxRatio=" + this.getMaxRatio() + ", maxSeedingTime=" + this.getMaxSeedingTime() + ", name=" + this.getName() + ", numComplete=" + this.getNumComplete() + ", numIncomplete=" + this.getNumIncomplete() + ", numLeechs=" + this.getNumLeechs() + ", numSeeds=" + this.getNumSeeds() + ", priority=" + this.getPriority() + ", progress=" + this.getProgress() + ", ratio=" + this.getRatio() + ", ratioLimit=" + this.getRatioLimit() + ", savePath=" + this.getSavePath() + ", seedingTime=" + this.getSeedingTime() + ", seedingTimeLimit=" + this.getSeedingTimeLimit() + ", seenComplete=" + this.getSeenComplete() + ", seqDl=" + this.getSeqDl() + ", size=" + this.getSize() + ", state=" + this.getState() + ", superSeeding=" + this.getSuperSeeding() + ", tags=" + this.getTags() + ", timeActive=" + this.getTimeActive() + ", totalSize=" + this.getTotalSize() + ", tracker=" + this.getTracker() + ", trackersCount=" + this.getTrackersCount() + ", upLimit=" + this.getUpLimit() + ", uploaded=" + this.getUploaded() + ", uploadedSession=" + this.getUploadedSession() + ", upspeed=" + this.getUpspeed() + ")";
    }
}
