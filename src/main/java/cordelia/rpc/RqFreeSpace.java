package cordelia.rpc;

import lombok.RequiredArgsConstructor;

@ReqMethod(value = "free-space", answer = RsFreeSpace.class)
@RequiredArgsConstructor
public final class RqFreeSpace implements RqArguments {

    private final String path;

}
