package cordelia.rpc;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public final class RsFreeSpace implements RsArguments {

    private String path;

    @SerializedName("size-bytes")
    private Long sizeBytes;

    @SerializedName("total_size")
    private Long totalSize;

}
