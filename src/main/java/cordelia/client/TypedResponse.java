package cordelia.client;

import com.google.gson.annotations.SerializedName;
import cordelia.rpc.RsArguments;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public final class TypedResponse<T extends RsArguments> {

    private Long tag;

    private String result;

    @SerializedName("arguments")
    private T args;

    public boolean isSuccess() {
        return "success".equals(result);
    }

    @SuppressWarnings("unchecked")
    public TypedResponse(Long tag, String result, RsArguments args) {
        this.tag = tag;
        this.result = result;
        this.args = (T) args;
    }

}
