package cordelia.rpc;

import com.google.gson.annotations.SerializedName;
import cordelia.rpc.types.Encryption;
import lombok.Builder;

@Builder
@ReqMethod(value = "session-set")
public final class RqSessionSet implements RqArguments {

    @SerializedName("alt-speed-down")
    private Long altSpeedDown;

    @SerializedName("alt-speed-enabled")
    private Boolean altSpeedEnabled;

    @SerializedName("alt-speed-time-begin")
    private Integer altSpeedTimeBegin;

    @SerializedName("alt-speed-time-day")
    private Integer altSpeedTimeDay;

    @SerializedName("alt-speed-time-enabled")
    private Boolean altSpeedTimeEnabled;

    @SerializedName("alt-speed-time-end")
    private Integer altSpeedTimeEnd;

    @SerializedName("alt-speed-up")
    private Long altSpeedUp;

    @SerializedName("blocklist-enabled")
    private Boolean blocklistEnabled;

    @SerializedName("blocklist-url")
    private String blocklistUrl;

    @SerializedName("cache-size-mb")
    private Integer cacheSize;

    @SerializedName("default-trackers")
    private String defaultTrackers;

    @SerializedName("dht-enabled")
    private Boolean dhtEnabled;

    @SerializedName("download-dir")
    private String downloadDir;

    @SerializedName("download-queue-enabled")
    private Boolean downloadQueueEnabled;

    @SerializedName("download-queue-size")
    private Integer downloadQueueSize;

    private Encryption encryption;

    @SerializedName("idle-seeding-limit")
    private Boolean idleSeedingLimit;

    @SerializedName("incomplete-dir-enabled")
    private Boolean incompleteDirEnabled;

    @SerializedName("incomplete-dir")
    private String incompleteDir;

    @SerializedName("lpd-enabled")
    private Boolean lpdEnabled;

    @SerializedName("peer-limit-global")
    private Integer peerLimitGlobal;

    @SerializedName("peer-limit-per-torrent")
    private Integer peerLimitPerTorrent;

    @SerializedName("peer-port-random-on-start")
    private Boolean peerPortRandomOnStart;

    @SerializedName("peer-port")
    private Integer peerPort;

    @SerializedName("pex-enabled")
    private Boolean pexEnabled;

    @SerializedName("port-forwarding-enabled")
    private Boolean portForwardingEnabled;

    @SerializedName("queue-stalled-enabled")
    private Boolean queueStalledEnabled;

    @SerializedName("queue-stalled-minutes")
    private Integer queueStalledMinutes;

    @SerializedName("rename-partial-files")
    private Boolean renamePartialFiles;

    @SerializedName("script-torrent-added-enabled")
    private Boolean scriptTorrentAddedEnabled;

    @SerializedName("script-torrent-added-filename")
    private String scriptTorrentAddedFilename;

    @SerializedName("script-torrent-done-enabled")
    private Boolean scriptTorrentDoneEnabled;

    @SerializedName("script-torrent-done-filename")
    private String scriptTorrentDoneFilename;

    @SerializedName("script-torrent-done-seeding-enabled")
    private Boolean scriptTorrentDoneSeedingEnabled;

    @SerializedName("script-torrent-done-seeding-filename")
    private String scriptTorrentDoneSeedingFilename;

    @SerializedName("seed-queue-enabled")
    private Boolean seedQueueEnabled;

    @SerializedName("seed-queue-size")
    private Integer seedQueueSize;

    private Float seedRatioLimit;

    private Boolean seedRatioLimited;

    @SerializedName("speed-limit-down-enabled")
    private Boolean speedLimitDownEnabled;

    @SerializedName("speed-limit-down")
    private Integer speedLimitDown;

    @SerializedName("speed-limit-up-enabled")
    private Boolean speedLimitUpEnabled;

    @SerializedName("speed-limit-up")
    private Integer speedLimitUp;

    @SerializedName("start-added-torrents")
    private Boolean startAddedTorrents;

    @SerializedName("trash-original-files")
    private Boolean trashOriginalFiles;

    @SerializedName("utp-enabled")
    private Boolean utpEnabled;

}
