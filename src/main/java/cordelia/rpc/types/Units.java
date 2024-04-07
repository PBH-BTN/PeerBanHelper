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
public final class Units {

    @SerializedName("speed-units")
    private List<String> speedUnits;

    @SerializedName("speed-bytes")
    private Integer speedBytes;

    @SerializedName("size-units")
    private List<String> sizeUnits;

    @SerializedName("size-bytes")
    private Integer sizeBytes;

    @SerializedName("memory-units")
    private List<String> memoryUnits;

    @SerializedName("memory-bytes")
    private Integer memoryBytes;

}
