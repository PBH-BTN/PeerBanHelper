CREATE TABLE alert
(
    id         BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    create_at  datetime     NOT NULL,
    read_at    datetime NULL,
    level      VARCHAR(255) NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    LONGTEXT     NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE banlist
(
    address  VARCHAR(255) NOT NULL,
    metadata LONGTEXT     NOT NULL,
    PRIMARY KEY (address)
);

CREATE TABLE `history`
(
    id               BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    ban_at           datetime     NOT NULL,
    unban_at         datetime     NOT NULL,
    ip               VARCHAR(255) NOT NULL,
    port             INT UNSIGNED                   NOT NULL,
    peer_id          VARCHAR(255) NULL,
    peer_client_name TEXT NULL,
    peer_uploaded    BIGINT NULL,
    peer_downloaded  BIGINT NULL,
    peer_progress DOUBLE NOT NULL,
    downloader_progress DOUBLE NOT NULL,
    torrent_id       BIGINT UNSIGNED                NOT NULL,
    module_name      VARCHAR(255) NOT NULL,
    rule_name        VARCHAR(255) NOT NULL,
    `description`    LONGTEXT     NOT NULL,
    flags            VARCHAR(255) NULL,
    downloader       VARCHAR(255) NOT NULL,
    structured_data  JSON NULL,
    peer_geoip       JSON NULL,
    PRIMARY KEY (id)
);

CREATE TABLE metadata
(
    `k` VARCHAR(255) NOT NULL,
    `v` LONGTEXT NULL,
    PRIMARY KEY (`k`)
);

CREATE TABLE pcb_address
(
    id                               BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    ip                               VARCHAR(255) NOT NULL,
    port                             INT UNSIGNED                   NOT NULL,
    torrent_id VARCHAR(255) NOT NULL,
    last_report_progress DOUBLE NOT NULL,
    last_report_uploaded             BIGINT NULL,
    tracking_uploaded_increase_total BIGINT NULL,
    rewind_counter                   INT          NOT NULL,
    progress_difference_counter      INT          NOT NULL,
    first_time_seen                  datetime     NOT NULL,
    last_time_seen                   datetime     NOT NULL,
    downloader                       VARCHAR(255) NOT NULL,
    ban_delay_window_end_at          datetime     NOT NULL,
    fast_pcb_test_execute_at         datetime     NOT NULL,
    last_torrent_completed_size      datetime     NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE pcb_range
(
    id                               BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    `range`                          VARCHAR(255) NOT NULL,
    port                             INT UNSIGNED                   NOT NULL,
    torrent_id VARCHAR(255) NOT NULL,
    last_report_progress DOUBLE NOT NULL,
    last_report_uploaded             BIGINT NULL,
    tracking_uploaded_increase_total BIGINT NULL,
    rewind_counter                   INT          NOT NULL,
    progress_difference_counter      INT          NOT NULL,
    first_time_seen                  datetime     NOT NULL,
    last_time_seen                   datetime     NOT NULL,
    downloader                       VARCHAR(255) NOT NULL,
    ban_delay_window_end_at          datetime     NOT NULL,
    fast_pcb_test_execute_at         datetime     NOT NULL,
    last_torrent_completed_size      datetime     NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE peer_connection_metrics
(
    id                               BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    timeframe_at                     datetime     NOT NULL,
    downloader                       VARCHAR(255) NOT NULL,
    total_connections                BIGINT UNSIGNED                NOT NULL,
    incoming_connections             BIGINT UNSIGNED                NOT NULL,
    remote_refuse_transfer_to_client BIGINT UNSIGNED                NOT NULL,
    remote_accept_transfer_to_client BIGINT UNSIGNED                NOT NULL,
    local_refuse_transfer_to_peer    BIGINT UNSIGNED                NOT NULL,
    local_accept_transfer_to_peer    BIGINT UNSIGNED                NOT NULL,
    local_not_interested             BIGINT UNSIGNED                NOT NULL,
    question_status                  BIGINT UNSIGNED                NOT NULL,
    optimistic_unchoke               BIGINT UNSIGNED                NOT NULL,
    from_dht                         BIGINT UNSIGNED                NOT NULL,
    from_pex                         BIGINT UNSIGNED                NOT NULL,
    from_lsd                         BIGINT UNSIGNED                NOT NULL,
    from_tracker_or_other            BIGINT UNSIGNED                NOT NULL,
    rc4_encrypted                    BIGINT UNSIGNED                NOT NULL,
    plain_text_encrypted             BIGINT UNSIGNED                NOT NULL,
    utp_socket                       BIGINT UNSIGNED                NOT NULL,
    tcp_socket                       BIGINT UNSIGNED                NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE peer_connection_metrics_track
(
    id           BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    timeframe_at datetime     NOT NULL,
    downloader   VARCHAR(255) NOT NULL,
    torrent_id   BIGINT UNSIGNED                NOT NULL,
    address      VARCHAR(255) NOT NULL,
    port         INT UNSIGNED                   NOT NULL,
    peer_id      VARCHAR(255) NOT NULL,
    client_name TEXT NULL,
    last_flags   VARCHAR(255) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE peer_records
(
    id                BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    address           VARCHAR(255) NOT NULL,
    port              INT UNSIGNED                   NOT NULL,
    torrent_id        BIGINT       NOT NULL,
    downloader        VARCHAR(255) NOT NULL,
    peer_id           VARCHAR(255) NULL,
    client_name TEXT NULL,
    uploaded          BIGINT UNSIGNED      NOT NULL,
    uploaded_offset   BIGINT UNSIGNED                NOT NULL,
    upload_speed      BIGINT UNSIGNED                NOT NULL,
    downloaded        BIGINT UNSIGNED                NOT NULL,
    downloaded_offset BIGINT UNSIGNED                NOT NULL,
    download_speed    BIGINT UNSIGNED                NOT NULL,
    last_flags        VARCHAR(255) NULL,
    first_time_seen   datetime     NOT NULL,
    last_time_seen    datetime     NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE torrents
(
    id BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    info_hash       VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    size            BIGINT UNSIGNED  NOT NULL,
    private_torrent TINYINT UNSIGNED NULL,
    PRIMARY KEY (id)
);

CREATE TABLE traffic_journal_v3
(
    id                                   BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    timestamp                            datetime     NOT NULL,
    downloader                           VARCHAR(255) NOT NULL,
    data_overall_uploaded_at_start       BIGINT UNSIGNED                NOT NULL,
    data_overall_uploaded                BIGINT UNSIGNED                NOT NULL,
    data_overall_downloaded_at_start     BIGINT UNSIGNED                NOT NULL,
    data_overall_downloaded              BIGINT UNSIGNED                NOT NULL,
    protocol_overall_uploaded_at_start   BIGINT UNSIGNED                NOT NULL,
    protocol_overall_uploaded            BIGINT UNSIGNED                NOT NULL,
    protocol_overall_downloaded_at_start BIGINT UNSIGNED                NOT NULL,
    protocol_overall_downloaded          BIGINT UNSIGNED                NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE `tracked_swarm`
(
    `id`                 bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `ip`                 varchar(255) NOT NULL,
    `port`               int UNSIGNED NOT NULL,
    `info_hash`          varchar(255) NOT NULL,
    `torrent_is_private` tinyint UNSIGNED NOT NULL,
    `downloader`         varchar(255) NOT NULL,
    `downloader_progress` double NOT NULL,
    `peer_id`            varchar(255) NULL,
    `client_name`        text NULL,
    `peer_progress`      varchar(255) NOT NULL,
    `uploaded`           bigint       NOT NULL,
    `uploaded_offset`    bigint       NOT NULL,
    `upload_speed`       bigint       NOT NULL,
    `downloaded`         bigint       NOT NULL,
    `downloaded_offset`  bigint       NOT NULL,
    `download_speed`     bigint       NOT NULL,
    `last_flags`         varchar(255) NULL,
    `first_time_seen`    datetime     NOT NULL,
    `last_time_seen`     datetime     NOT NULL,
    `download_speed_max` bigint       NOT NULL,
    `upload_speed_max`   bigint       NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_tracked_swarm_unique`(`ip`, `port`, `info_hash`, `downloader`),
    INDEX                `idx_tracked_swarm_last_seen_time`(`last_time_seen` DESC)
);

CREATE TABLE `rule_sub_info`
(
    `rule_id`     varchar(255) NOT NULL,
    `enabled`     tinyint      NOT NULL,
    `rule_name`   varchar(255) NOT NULL,
    `sub_url`     varchar(255) NOT NULL,
    `last_update` datetime NULL,
    `ent_count`   int NULL,
    PRIMARY KEY (`rule_id`),
    INDEX         `idx_rule_sub_info_rule_id`(`rule_id`)
);

CREATE TABLE `rule_sub_log`
(
    `id`          bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `rule_id`     varchar(255) NOT NULL,
    `update_time` datetime     NOT NULL,
    `count`       int          NOT NULL,
    `update_type` varchar(255) NOT NULL,
    PRIMARY KEY (`id`),
    INDEX         `idx_rule_sub_logs_rule_id`(`rule_id`, `update_time` DESC)
);

ALTER TABLE pcb_address
    ADD CONSTRAINT idx_pcb_address_unique UNIQUE (ip, port, torrent_id, downloader);

ALTER TABLE pcb_range
    ADD CONSTRAINT idx_pcb_range_unique UNIQUE (`range`, port, torrent_id, downloader);

ALTER TABLE peer_connection_metrics_track
    ADD CONSTRAINT idx_peer_connection_metrics_track UNIQUE (timeframe_at, downloader, torrent_id, address, port);

ALTER TABLE peer_connection_metrics
    ADD CONSTRAINT idx_peer_connection_metrics_unique UNIQUE (timeframe_at, downloader);

ALTER TABLE peer_records
    ADD CONSTRAINT idx_peer_records_unique UNIQUE (address, port, torrent_id, downloader);

ALTER TABLE traffic_journal_v3
    ADD CONSTRAINT idx_traffic_journal_v3_unique UNIQUE (timestamp, downloader);

CREATE INDEX idx_alert_alertExists ON alert (read_at, identifier);

CREATE INDEX idx_alert_readAt ON alert (read_at);

CREATE INDEX idx_alert_unreadAlerts ON alert (create_at, read_at);

CREATE INDEX idx_history_downloader ON `history` (downloader);

CREATE INDEX idx_history_ip ON `history` (ip);

CREATE INDEX idx_history_module_name ON `history` (module_name);

CREATE INDEX idx_history_peer_id ON `history` (peer_id);

CREATE INDEX idx_history_torrent_id ON `history` (torrent_id);

CREATE INDEX idx_history_view ON `history` (ban_at);

CREATE INDEX idx_pcb_address_last_time_seen ON pcb_address (last_time_seen);

CREATE INDEX idx_pcb_range_last_time_seen ON pcb_range (last_time_seen);

CREATE INDEX idx_peer_records_address ON peer_records (address);

CREATE INDEX idx_peer_records_client_analyse ON peer_records (downloader, uploaded, downloaded, first_time_seen, last_time_seen);

CREATE INDEX idx_peer_records_last_time_seen ON peer_records (last_time_seen);

CREATE INDEX idx_peer_records_session_between ON peer_records (downloader, first_time_seen, last_time_seen);

CREATE INDEX idx_torrents_info_hash ON torrents (info_hash);

CREATE INDEX idx_torrents_name ON torrents (name);

CREATE INDEX idx_torrents_private_torrent ON torrents (private_torrent);