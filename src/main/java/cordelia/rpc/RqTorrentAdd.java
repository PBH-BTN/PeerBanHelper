package cordelia.rpc;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

import java.util.List;

@Builder
@ReqMethod(value = "torrent-add", answer = RsTorrentGet.class)
public final class RqTorrentAdd implements RqArguments {

    private String cookies;
    @SerializedName("download-dir")
    private String downloadDir;
    private String filename;
    private List<String> labels;
    private String metainfo;
    private Boolean paused;
    @SerializedName("peer-limit")
    private Integer peerLimit;
    private Integer bandwidthPriority;
    @SerializedName("files-wanted")
    private List<Integer> filesWanted;
    @SerializedName("files-unwanted")
    private List<Integer> filesUnwanted;
    @SerializedName("priority-high")
    private List<Integer> priorityHigh;
    @SerializedName("priority-low")
    private List<Integer> priorityLow;
    @SerializedName("priority-normal")
    private List<Integer> priorityNormal;

}
