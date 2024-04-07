package cordelia.rpc.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TorrentAction {

    START("torrent-start"),
    START_NOW("torrent-start-now"),
    STOP("torrent-stop"),
    VERIFY("torrent-verify"),
    REANNOUNCE("torrent-reannounce");

    @Getter
    private final String method;
}