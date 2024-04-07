package cordelia.rpc;

import lombok.RequiredArgsConstructor;

import java.util.List;

@ReqMethod(value = "group-get", answer = RsGroupGet.class)
@RequiredArgsConstructor
public final class RqGroupGet implements RqArguments {

    private final List<String> group;

    public RqGroupGet() {
        this(null);
    }

}
