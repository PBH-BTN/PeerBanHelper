package cordelia.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public final class TypedRequest {

    private Long tag;
    private String method;
    private Object arguments;

}
