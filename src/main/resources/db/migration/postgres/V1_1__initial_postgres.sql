-- PostgreSQL Initial Schema Migration
-- Converted from MySQL V1_1__initial_mysql.sql

CREATE TABLE alert
(
    id         BIGSERIAL PRIMARY KEY,
    create_at  TIMESTAMPTZ NOT NULL,
    read_at    TIMESTAMPTZ NULL,
    level      VARCHAR(255) NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    TEXT NOT NULL
);

CREATE TABLE banlist
(
    address  VARCHAR(255) PRIMARY KEY,
    metadata TEXT NOT NULL
);

CREATE TABLE history
(
    id                  BIGSERIAL PRIMARY KEY,
    ban_at              TIMESTAMPTZ NOT NULL,
    unban_at            TIMESTAMPTZ NOT NULL,
    ip                  INET NOT NULL,
    port                INTEGER NOT NULL,
    peer_id             VARCHAR(255) NULL,
    peer_client_name    TEXT NULL,
    peer_uploaded       BIGINT NULL,
    peer_downloaded     BIGINT NULL,
    peer_progress       DOUBLE PRECISION NOT NULL,
    downloader_progress DOUBLE PRECISION NOT NULL,
    torrent_id          BIGINT NOT NULL,
    module_name         VARCHAR(255) NOT NULL,
    rule_name           VARCHAR(255) NOT NULL,
    description         TEXT NOT NULL,
    flags               VARCHAR(255) NULL,
    downloader          VARCHAR(255) NOT NULL,
    structured_data     TEXT NULL,
    peer_geoip          TEXT NULL
);

CREATE TABLE metadata
(
    k VARCHAR(255) PRIMARY KEY,
    v TEXT NULL
);

CREATE TABLE pcb_address
(
    id                               BIGSERIAL PRIMARY KEY,
    ip                               INET NOT NULL,
    port                             INTEGER NOT NULL,
    torrent_id                       VARCHAR(255) NOT NULL,
    last_report_progress             DOUBLE PRECISION NOT NULL,
    last_report_uploaded             BIGINT NULL,
    tracking_uploaded_increase_total BIGINT NULL,
    rewind_counter                   INTEGER NOT NULL,
    progress_difference_counter      INTEGER NOT NULL,
    first_time_seen                  TIMESTAMPTZ NOT NULL,
    last_time_seen                   TIMESTAMPTZ NOT NULL,
    downloader                       VARCHAR(255) NOT NULL,
    ban_delay_window_end_at          TIMESTAMPTZ NOT NULL,
    fast_pcb_test_execute_at         TIMESTAMPTZ NOT NULL,
    last_torrent_completed_size      BIGINT NOT NULL
);

CREATE TABLE pcb_range
(
    id                               BIGSERIAL PRIMARY KEY,
    range                            VARCHAR(255) NOT NULL,
    torrent_id                       VARCHAR(255) NOT NULL,
    last_report_progress             DOUBLE PRECISION NOT NULL,
    last_report_uploaded             BIGINT NULL,
    tracking_uploaded_increase_total BIGINT NULL,
    rewind_counter                   INTEGER NOT NULL,
    progress_difference_counter      INTEGER NOT NULL,
    first_time_seen                  TIMESTAMPTZ NOT NULL,
    last_time_seen                   TIMESTAMPTZ NOT NULL,
    downloader                       VARCHAR(255) NOT NULL,
    ban_delay_window_end_at          TIMESTAMPTZ NOT NULL,
    fast_pcb_test_execute_at         TIMESTAMPTZ NOT NULL,
    last_torrent_completed_size      BIGINT NOT NULL
);

CREATE TABLE peer_connection_metrics
(
    id                               BIGSERIAL PRIMARY KEY,
    timeframe_at                     TIMESTAMPTZ NOT NULL,
    downloader                       VARCHAR(255) NOT NULL,
    total_connections                BIGINT NOT NULL,
    incoming_connections             BIGINT NOT NULL,
    remote_refuse_transfer_to_client BIGINT NOT NULL,
    remote_accept_transfer_to_client BIGINT NOT NULL,
    local_refuse_transfer_to_peer    BIGINT NOT NULL,
    local_accept_transfer_to_peer    BIGINT NOT NULL,
    local_not_interested             BIGINT NOT NULL,
    question_status                  BIGINT NOT NULL,
    optimistic_unchoke               BIGINT NOT NULL,
    from_dht                         BIGINT NOT NULL,
    from_pex                         BIGINT NOT NULL,
    from_lsd                         BIGINT NOT NULL,
    from_tracker_or_other            BIGINT NOT NULL,
    rc4_encrypted                    BIGINT NOT NULL,
    plain_text_encrypted             BIGINT NOT NULL,
    utp_socket                       BIGINT NOT NULL,
    tcp_socket                       BIGINT NOT NULL
);

CREATE TABLE peer_connection_metrics_track
(
    id           BIGSERIAL PRIMARY KEY,
    timeframe_at TIMESTAMPTZ NOT NULL,
    downloader   VARCHAR(255) NOT NULL,
    torrent_id   BIGINT NOT NULL,
    address      INET NOT NULL,
    port         INTEGER NOT NULL,
    peer_id      VARCHAR(255) NOT NULL,
    client_name  TEXT NULL,
    last_flags   VARCHAR(255) NULL
);

CREATE TABLE peer_records
(
    id                BIGSERIAL PRIMARY KEY,
    address           INET NOT NULL,
    port              INTEGER NOT NULL,
    torrent_id        BIGINT NOT NULL,
    downloader        VARCHAR(255) NOT NULL,
    peer_id           VARCHAR(255) NULL,
    client_name       TEXT NULL,
    uploaded          BIGINT NOT NULL,
    uploaded_offset   BIGINT NOT NULL,
    upload_speed      BIGINT NOT NULL,
    downloaded        BIGINT NOT NULL,
    downloaded_offset BIGINT NOT NULL,
    download_speed    BIGINT NOT NULL,
    last_flags        VARCHAR(255) NULL,
    first_time_seen   TIMESTAMPTZ NOT NULL,
    last_time_seen    TIMESTAMPTZ NOT NULL,
    peer_geoip        TEXT NULL
);

CREATE TABLE torrents
(
    id              BIGSERIAL PRIMARY KEY,
    info_hash       VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    size            BIGINT NOT NULL,
    private_torrent SMALLINT NULL
);

CREATE TABLE traffic_journal_v3
(
    id                                   BIGSERIAL PRIMARY KEY,
    timestamp                            TIMESTAMPTZ NOT NULL,
    downloader                           VARCHAR(255) NOT NULL,
    data_overall_uploaded_at_start       BIGINT NOT NULL,
    data_overall_uploaded                BIGINT NOT NULL,
    data_overall_downloaded_at_start     BIGINT NOT NULL,
    data_overall_downloaded              BIGINT NOT NULL,
    protocol_overall_uploaded_at_start   BIGINT NOT NULL,
    protocol_overall_uploaded            BIGINT NOT NULL,
    protocol_overall_downloaded_at_start BIGINT NOT NULL,
    protocol_overall_downloaded          BIGINT NOT NULL
);

CREATE TABLE tracked_swarm
(
    id                  BIGSERIAL PRIMARY KEY,
    ip                  INET NOT NULL,
    port                INTEGER NOT NULL,
    info_hash           VARCHAR(255) NOT NULL,
    torrent_is_private  SMALLINT NOT NULL,
    torrent_size        BIGINT NOT NULL,
    downloader          VARCHAR(255) NOT NULL,
    downloader_progress DOUBLE PRECISION NOT NULL,
    peer_id             VARCHAR(255) NULL,
    client_name         TEXT NULL,
    peer_progress       VARCHAR(255) NOT NULL,
    uploaded            BIGINT NOT NULL,
    uploaded_offset     BIGINT NOT NULL,
    upload_speed        BIGINT NOT NULL,
    downloaded          BIGINT NOT NULL,
    downloaded_offset   BIGINT NOT NULL,
    download_speed      BIGINT NOT NULL,
    last_flags          VARCHAR(255) NULL,
    first_time_seen     TIMESTAMPTZ NOT NULL,
    last_time_seen      TIMESTAMPTZ NOT NULL,
    download_speed_max  BIGINT NOT NULL,
    upload_speed_max    BIGINT NOT NULL
);

CREATE TABLE rule_sub_info
(
    rule_id     VARCHAR(255) PRIMARY KEY,
    enabled     SMALLINT NOT NULL,
    rule_name   VARCHAR(255) NOT NULL,
    sub_url     VARCHAR(255) NOT NULL,
    last_update TIMESTAMPTZ NULL,
    ent_count   INTEGER NULL
);

CREATE TABLE rule_sub_log
(
    id          BIGSERIAL PRIMARY KEY,
    rule_id     VARCHAR(255) NOT NULL,
    update_time TIMESTAMPTZ NOT NULL,
    count       INTEGER NOT NULL,
    update_type VARCHAR(255) NOT NULL
);

-- Unique constraints
ALTER TABLE pcb_address ADD CONSTRAINT idx_pcb_address_unique UNIQUE (ip, port, torrent_id, downloader);
ALTER TABLE pcb_range ADD CONSTRAINT idx_pcb_range_unique UNIQUE (range, torrent_id, downloader);
ALTER TABLE peer_connection_metrics_track ADD CONSTRAINT idx_peer_connection_metrics_track UNIQUE (timeframe_at, downloader, torrent_id, address, port);
ALTER TABLE peer_connection_metrics ADD CONSTRAINT idx_peer_connection_metrics_unique UNIQUE (timeframe_at, downloader);
ALTER TABLE peer_records ADD CONSTRAINT idx_peer_records_unique UNIQUE (address, port, torrent_id, downloader);
ALTER TABLE traffic_journal_v3 ADD CONSTRAINT idx_traffic_journal_v3_unique UNIQUE (timestamp, downloader);
ALTER TABLE tracked_swarm ADD CONSTRAINT idx_tracked_swarm_unique UNIQUE (ip, port, info_hash, downloader);
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
