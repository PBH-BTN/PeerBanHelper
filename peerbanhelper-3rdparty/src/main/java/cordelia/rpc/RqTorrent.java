package cordelia.rpc;

import cordelia.client.TypedRequest;
import cordelia.rpc.types.TorrentAction;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@ReqMethod
public final class RqTorrent implements RqArguments {

    private final TorrentAction torrentAction;
    private final List<Object> ids;

    public RqTorrent(TorrentAction torrentAction) {
        this(torrentAction, null);
    }

    public void add(Object id) {
        ids.add(id);
    }

    @Override
    public TypedRequest toReq(Long tag) {
        Map<String, Object> arguments = collectFields();
        arguments.remove("action");
        return new TypedRequest(tag, torrentAction.getMethod(), arguments);
    }

}
