package cordelia.rpc;

import com.google.gson.annotations.SerializedName;
import cordelia.rpc.types.Stats;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@NoArgsConstructor
public final class RsSessionStats implements RsArguments {

    private Long activeTorrentCount;
    private Long downloadSpeed;
    private Long pausedTorrentCount;
    private Long torrentCount;
    private Long uploadSpeed;

    @SerializedName("cumulative-stats")
    private Stats cumulativeStats;

    @SerializedName("current-stats")
    private Stats currentStats;

}
