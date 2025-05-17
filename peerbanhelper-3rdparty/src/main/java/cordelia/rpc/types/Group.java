package cordelia.rpc.types;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public final class Group {

    private Boolean honorsSessionLimits;
    private String name;
    @SerializedName("speed-limit-down-enabled")
    private Boolean speedLimitDownEnabled;
    @SerializedName("speed-limit-down")
    private Long speedLimitDown;
    @SerializedName("speed-limit-up-enabled")
    private Boolean speedLimitUpEnabled;
    @SerializedName("speed-limit-up")
    private Long speedLimitUp;

}
