package cordelia.rpc;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@ReqMethod("group-set")
@Builder
public final class RqGroupSet implements RqArguments {

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
