package cordelia.rpc;

import lombok.RequiredArgsConstructor;

import java.util.List;

@ReqMethod(value = "torrent-stop")
@RequiredArgsConstructor
public final class RqTorrentStop implements RqArguments {

    private final List<Object> ids;

}
