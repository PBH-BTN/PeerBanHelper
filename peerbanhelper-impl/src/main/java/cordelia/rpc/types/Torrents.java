package cordelia.rpc.types;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public final class Torrents {

    private Long activityDate;
    private Long addedDate;
    private List<Integer> availability;
    private Integer bandwidthPriority;
    private String comment;
    private Long corruptEver;
    private String creator;
    private Long dateCreated;
    private Integer desiredAvailable;
    private Long doneDate;
    private String downloadDir;
    private Long downloadedEver;
    private Integer downloadLimit;
    private Boolean downloadLimited;
    private Long editDate;
    private Long error;
    private String errorString;
    private Long eta;
    private Long etaIdle;
    @SerializedName("file-count")
    private Long fileCount;
    private List<Files> files;
    private List<FileStats> fileStats;
    private String group;
    private String hashString;
    private Integer haveUnchecked;
    private Integer haveValid;
    private Boolean honorsSessionLimits;
    private Long id;
    private Boolean isFinished;
    private Boolean isPrivate;
    private Boolean isStalled;
    private List<String> labels;
    private Long leftUntilDone;
    private String magnetLink;
    private Long manualAnnounceTime;
    private Integer maxConnectedPeers;
    private Double metadataPercentComplete;
    private String name;
    @SerializedName("peer-limit")
    private Integer peerLimit;
    private List<Peers> peers;
    private Integer peersConnected;
    private PeersFrom peersFrom;
    private Integer peersGettingFromUs;
    private Integer peersSendingToUs;
    private Double percentComplete;
    private Double percentDone;
    private String pieces;
    private Long pieceCount;
    private Long pieceSize;
    private List<Integer> priorities;
    @SerializedName("primary-mime-type")
    private String primaryMimeType;
    private Integer queuePosition;
    private Long rateDownload;
    private Long rateUpload;
    private Double recheckProgress;
    private Long secondsDownloading;
    private Long secondsSeeding;
    private Long seedIdleLimit;
    private Integer seedIdleMode;
    private Double seedRatioLimit;
    private Integer seedRatioMode;
    private Boolean sequentialDownload;
    private Long sizeWhenDone;
    private Long startDate;
    private Status status;
    private List<Trackers> trackers;
    private String trackerList;
    private List<TrackerStats> trackerStats;
    private Long totalSize;
    private String torrentFile;
    private Long uploadedEver;
    private Long uploadLimit;
    private Boolean uploadLimited;
    private Double uploadRatio;
    private List<Integer> wanted;
    private List<String> webseeds;
    private Long webseedsSendingToUs;

}
