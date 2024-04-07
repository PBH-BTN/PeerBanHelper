package cordelia.client;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public final class RawResponse {

    private Long tag;
    private String result;
    private Map<String, Object> arguments;

}
