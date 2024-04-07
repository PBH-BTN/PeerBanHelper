package cordelia.rpc;

import lombok.RequiredArgsConstructor;

import java.util.List;

@ReqMethod(value = "session-get", answer = RsSessionGet.class)
@RequiredArgsConstructor
public final class RqSessionGet implements RqArguments {

    private final List<String> fields;

    public RqSessionGet() {
        this(null);
    }
}
