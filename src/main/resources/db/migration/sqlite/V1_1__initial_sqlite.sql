-- SQLite Initial Schema Migration
-- Converted from MySQL V1_1__initial_mysql.sql

CREATE TABLE alert
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    create_at  INTEGER     NOT NULL,
    read_at    INTEGER NULL,
    level      TEXT NOT NULL,
    identifier TEXT NOT NULL,
    title      TEXT NOT NULL,
    content TEXT NOT NULL
);

CREATE TABLE banlist
(
    address  TEXT NOT NULL PRIMARY KEY,
    metadata TEXT NOT NULL
);

CREATE TABLE history
(
    id               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    ban_at           INTEGER     NOT NULL,
    unban_at         INTEGER     NOT NULL,
    ip               TEXT NOT NULL,
    port             INTEGER                   NOT NULL,
    peer_id          TEXT NULL,
    peer_client_name TEXT NULL,
    peer_uploaded    INTEGER NULL,
    peer_downloaded  INTEGER NULL,
    peer_progress REAL NOT NULL,
    downloader_progress REAL NOT NULL,
    torrent_id       INTEGER                NOT NULL,
    module_name      TEXT NOT NULL,
    rule_name        TEXT NOT NULL,
    description   TEXT NOT NULL,
    flags            TEXT NULL,
    downloader       TEXT NOT NULL,
    structured_data TEXT NULL,
    peer_geoip      TEXT NULL
);

CREATE TABLE metadata
(
    k TEXT NOT NULL PRIMARY KEY,
    v TEXT NULL
);

CREATE TABLE pcb_address
(
    id                               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    ip                               TEXT NOT NULL,
    port                             INTEGER                   NOT NULL,
    torrent_id                  TEXT NOT NULL,
    last_report_progress REAL NOT NULL,
    last_report_uploaded             INTEGER NULL,
    tracking_uploaded_increase_total INTEGER NULL,
    rewind_counter                   INTEGER          NOT NULL,
    progress_difference_counter      INTEGER          NOT NULL,
    first_time_seen                  INTEGER     NOT NULL,
    last_time_seen                   INTEGER     NOT NULL,
    downloader                       TEXT NOT NULL,
    ban_delay_window_end_at          INTEGER     NOT NULL,
    fast_pcb_test_execute_at         INTEGER     NOT NULL,
    last_torrent_completed_size INTEGER       NOT NULL
);

CREATE TABLE pcb_range
(
    id                               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    "range"                          TEXT NOT NULL,
    torrent_id                  TEXT NOT NULL,
    last_report_progress REAL NOT NULL,
    last_report_uploaded             INTEGER NULL,
    tracking_uploaded_increase_total INTEGER NULL,
    rewind_counter                   INTEGER          NOT NULL,
    progress_difference_counter      INTEGER          NOT NULL,
    first_time_seen                  INTEGER     NOT NULL,
    last_time_seen                   INTEGER     NOT NULL,
    downloader                       TEXT NOT NULL,
    ban_delay_window_end_at          INTEGER     NOT NULL,
    fast_pcb_test_execute_at         INTEGER     NOT NULL,
    last_torrent_completed_size INTEGER       NOT NULL
);

CREATE TABLE peer_connection_metrics
(
    id                               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    timeframe_at                     INTEGER     NOT NULL,
    downloader                       TEXT NOT NULL,
    total_connections                INTEGER                NOT NULL,
    incoming_connections             INTEGER                NOT NULL,
    remote_refuse_transfer_to_client INTEGER                NOT NULL,
    remote_accept_transfer_to_client INTEGER                NOT NULL,
    local_refuse_transfer_to_peer    INTEGER                NOT NULL,
    local_accept_transfer_to_peer    INTEGER                NOT NULL,
    local_not_interested             INTEGER                NOT NULL,
    question_status                  INTEGER                NOT NULL,
    optimistic_unchoke               INTEGER                NOT NULL,
    from_dht                         INTEGER                NOT NULL,
    from_pex                         INTEGER                NOT NULL,
    from_lsd                         INTEGER                NOT NULL,
    from_tracker_or_other            INTEGER                NOT NULL,
    rc4_encrypted                    INTEGER                NOT NULL,
    plain_text_encrypted             INTEGER                NOT NULL,
    utp_socket                       INTEGER                NOT NULL,
    tcp_socket                       INTEGER                NOT NULL
);

CREATE TABLE peer_connection_metrics_track
(
    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    timeframe_at INTEGER     NOT NULL,
    downloader   TEXT NOT NULL,
    torrent_id   INTEGER                NOT NULL,
    address      TEXT NOT NULL,
    port         INTEGER                   NOT NULL,
    peer_id      TEXT NOT NULL,
    client_name TEXT NULL,
    last_flags   TEXT NULL
);

CREATE TABLE peer_records
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    address           TEXT NOT NULL,
    port              INTEGER                   NOT NULL,
    torrent_id        INTEGER       NOT NULL,
    downloader        TEXT NOT NULL,
    peer_id           TEXT NULL,
    client_name       TEXT NULL,
    uploaded          INTEGER NOT NULL,
    uploaded_offset   INTEGER NOT NULL,
    upload_speed      INTEGER NOT NULL,
    downloaded        INTEGER NOT NULL,
    downloaded_offset INTEGER NOT NULL,
    download_speed    INTEGER NOT NULL,
    last_flags        TEXT NULL,
    first_time_seen   INTEGER     NOT NULL,
    last_time_seen    INTEGER     NOT NULL,
    peer_geoip        TEXT NULL
);

CREATE TABLE torrents
(
    id   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    info_hash       TEXT NOT NULL,
    name            TEXT NOT NULL,
    size INTEGER NOT NULL,
    private_torrent INTEGER NULL
);

CREATE TABLE traffic_journal_v3
(
    id                                   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    timestamp                            INTEGER     NOT NULL,
    downloader                           TEXT NOT NULL,
    data_overall_uploaded_at_start       INTEGER                NOT NULL,
    data_overall_uploaded                INTEGER                NOT NULL,
    data_overall_downloaded_at_start     INTEGER                NOT NULL,
    data_overall_downloaded              INTEGER                NOT NULL,
    protocol_overall_uploaded_at_start   INTEGER                NOT NULL,
    protocol_overall_uploaded            INTEGER                NOT NULL,
    protocol_overall_downloaded_at_start INTEGER                NOT NULL,
    protocol_overall_downloaded          INTEGER                NOT NULL
);

CREATE TABLE tracked_swarm
(
    id                 INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    ip                 TEXT NOT NULL,
    port               INTEGER NOT NULL,
    info_hash          TEXT NOT NULL,
    torrent_is_private INTEGER NOT NULL,
    torrent_size       INTEGER       NOT NULL,
    downloader         TEXT NOT NULL,
    downloader_progress REAL NOT NULL,
    peer_id            TEXT NULL,
    client_name        TEXT NULL,
    peer_progress      TEXT NOT NULL,
    uploaded           INTEGER       NOT NULL,
    uploaded_offset    INTEGER       NOT NULL,
    upload_speed       INTEGER       NOT NULL,
    downloaded         INTEGER       NOT NULL,
    downloaded_offset  INTEGER       NOT NULL,
    download_speed     INTEGER       NOT NULL,
    last_flags         TEXT NULL,
    first_time_seen    INTEGER     NOT NULL,
    last_time_seen     INTEGER     NOT NULL,
    download_speed_max INTEGER       NOT NULL,
    upload_speed_max   INTEGER       NOT NULL
);

CREATE TABLE rule_sub_info
(
    rule_id     TEXT NOT NULL PRIMARY KEY,
    enabled     INTEGER      NOT NULL,
    rule_name   TEXT NOT NULL,
    sub_url     TEXT NOT NULL,
    last_update INTEGER NULL,
    ent_count   INTEGER NULL
);

CREATE TABLE rule_sub_log
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    rule_id     TEXT NOT NULL,
    update_time INTEGER     NOT NULL,
    count       INTEGER          NOT NULL,
    update_type TEXT NOT NULL
);

-- Unique constraints (SQLite uses CREATE UNIQUE INDEX instead of ALTER TABLE ADD CONSTRAINT)
CREATE UNIQUE INDEX idx_pcb_address_unique ON pcb_address (ip, port, torrent_id, downloader);
CREATE UNIQUE INDEX idx_pcb_range_unique ON pcb_range ("range", torrent_id, downloader);
CREATE UNIQUE INDEX idx_peer_connection_metrics_track ON peer_connection_metrics_track (timeframe_at, downloader, torrent_id, address, port);
CREATE UNIQUE INDEX idx_peer_connection_metrics_unique ON peer_connection_metrics (timeframe_at, downloader);
CREATE UNIQUE INDEX idx_peer_records_unique ON peer_records (address, port, torrent_id, downloader);
CREATE UNIQUE INDEX idx_traffic_journal_v3_unique ON traffic_journal_v3 (timestamp, downloader);
CREATE UNIQUE INDEX idx_tracked_swarm_unique ON tracked_swarm (ip, port, info_hash, downloader);
CREATE UNIQUE INDEX idx_torrents_info_hash ON torrents (info_hash);

-- Regular indexes
CREATE INDEX idx_alert_alertExists ON alert (read_at, identifier);
CREATE INDEX idx_alert_readAt ON alert (read_at);
CREATE INDEX idx_alert_unreadAlerts ON alert (create_at, read_at);
CREATE INDEX idx_history_downloader ON history (downloader);
CREATE INDEX idx_history_ip ON history (ip);
CREATE INDEX idx_history_module_name ON history (module_name);
CREATE INDEX idx_history_peer_id ON history (peer_id);
CREATE INDEX idx_history_torrent_id ON history (torrent_id);
CREATE INDEX idx_history_view ON history (ban_at);
CREATE INDEX idx_pcb_address_last_time_seen ON pcb_address (last_time_seen);
CREATE INDEX idx_pcb_range_last_time_seen ON pcb_range (last_time_seen);
CREATE INDEX idx_peer_records_address ON peer_records (address);
CREATE INDEX idx_peer_records_client_analyse ON peer_records (downloader, uploaded, downloaded, first_time_seen, last_time_seen);
CREATE INDEX idx_peer_records_last_time_seen ON peer_records (last_time_seen);
CREATE INDEX idx_peer_records_session_between ON peer_records (downloader, first_time_seen, last_time_seen);
CREATE INDEX idx_torrents_name ON torrents (name);
CREATE INDEX idx_torrents_private_torrent ON torrents (private_torrent);
CREATE INDEX idx_tracked_swarm_last_seen_time ON tracked_swarm (last_time_seen DESC);
CREATE INDEX idx_rule_sub_info_rule_id ON rule_sub_info (rule_id);
CREATE INDEX idx_rule_sub_logs_rule_id ON rule_sub_log (rule_id, update_time DESC);
