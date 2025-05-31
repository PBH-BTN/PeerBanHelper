package cordelia.rpc.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TorrentAction {

    START("torrent-start"),
    START_NOW("torrent-start-now"),
    STOP("torrent-stop"),
    VERIFY("torrent-verify"),
    REANNOUNCE("torrent-reannounce");

    private final String method;
}