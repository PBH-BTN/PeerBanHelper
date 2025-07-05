package cordelia.rpc;

import lombok.RequiredArgsConstructor;

import java.util.List;

@ReqMethod(value = "torrent-start")
@RequiredArgsConstructor
public final class RqTorrentStart implements RqArguments {

    private final List<Object> ids;

}
