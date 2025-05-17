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
public final class RsBlockList implements RsArguments {

    @SerializedName("blocklist-size")
    private Integer blockListSize;

}
