ALTER TABLE peer_connection_metrics_track RENAME TO peer_connection_metrics_track_old;

CREATE TABLE peer_connection_metrics_track
(
    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    timeframe_at INTEGER     NOT NULL,
    downloader   TEXT NOT NULL,
    torrent_id   INTEGER                NOT NULL,
    address      TEXT NOT NULL,
    port         INTEGER                   NOT NULL,
    peer_id      TEXT NULL,
    client_name TEXT NULL,
    last_flags   TEXT NULL
);

INSERT INTO peer_connection_metrics_track
(
    id, timeframe_at, downloader, torrent_id, address, port, peer_id, client_name, last_flags
)
SELECT
    id, timeframe_at, downloader, torrent_id, address, port, peer_id, client_name, last_flags
FROM peer_connection_metrics_track_old;

DROP TABLE peer_connection_metrics_track_old;

CREATE UNIQUE INDEX idx_peer_connection_metrics_track ON peer_connection_metrics_track (timeframe_at, downloader, torrent_id, address, port);