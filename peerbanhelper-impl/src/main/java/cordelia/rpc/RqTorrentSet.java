package cordelia.rpc;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Builder
@ReqMethod("torrent-set")
public final class RqTorrentSet implements RqArguments {

    private Integer bandwidthPriority;
    private Long downloadLimit;
    private Boolean downloadLimited;
    @SerializedName("files-unwanted")
    private List<Integer> filesUnwanted;
    @SerializedName("files-wanted")
    private List<Integer> filesWanted;
    private String group;
    private Boolean honorsSessionLimits;
    private List<Object> ids;
    private List<String> labels;
    private String location;
    @SerializedName("peer-limit")
    private Integer peerLimit;
    @SerializedName("priority-high")
    private List<Integer> priorityHigh;
    @SerializedName("priority-low")
    private List<Integer> priorityLow;
    @SerializedName("priority-normal")
    private List<Integer> priorityNormal;
    private Integer queuePosition;
    private Integer seedIdleLimit;
    private Integer seedIdleMode;
    private Float seedRatioLimit;
    private Integer seedRatioMode;
    private Boolean sequentialDownload;
    private String trackerList;
    private Long uploadLimit;
    private Boolean uploadLimited;

}
