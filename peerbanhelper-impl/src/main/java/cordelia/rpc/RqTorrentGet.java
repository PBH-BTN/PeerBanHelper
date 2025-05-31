package cordelia.rpc;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@ReqMethod(value = "torrent-get", answer = RsTorrentGet.class)
public final class RqTorrentGet implements RqArguments {

    private final List<Object> ids;
    private final List<String> fields;
    private final String format;

    public RqTorrentGet(String... fields) {
        this(null, List.of(fields));
    }

    public RqTorrentGet(List<String> fields) {
        this(null, fields);
    }

    public RqTorrentGet(List<Object> ids, List<String> fields) {
        this(ids, fields, "objects");
    }

}
