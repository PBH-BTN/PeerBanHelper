package cordelia.rpc;

import lombok.RequiredArgsConstructor;

import java.util.List;

@ReqMethod(value = "torrent-rename-path", answer = RsTorrentRename.class)
@RequiredArgsConstructor
public final class RqTorrentRename implements RqArguments {

    private final List<Object> ids;
    private final String path;
    private final String name;

}
