/*
 Navicat Premium Dump SQL

 Source Server         : peerbanhelper
 Source Server Type    : SQLite
 Source Server Version : 3045000 (3.45.0)
 Source Schema         : main

 Target Server Type    : SQLite
 Target Server Version : 3045000 (3.45.0)
 File Encoding         : 65001

 Date: 25/01/2026 22:09:11
*/

PRAGMA
foreign_keys = false;

-- ----------------------------
-- Table structure for alert
-- ----------------------------
DROP TABLE IF EXISTS "alert";
CREATE TABLE "alert"
(
    "id"         INTEGER PRIMARY KEY AUTOINCREMENT,
    "createAt"   TIMESTAMP NOT NULL,
    "readAt"     TIMESTAMP,
    "level"      VARCHAR   NOT NULL,
    "identifier" VARCHAR   NOT NULL,
    "title"      VARCHAR   NOT NULL,
    "content"    VARCHAR   NOT NULL
);

-- ----------------------------
-- Table structure for banlist
-- ----------------------------
DROP TABLE IF EXISTS "banlist";
CREATE TABLE "banlist"
(
    "address"  VARCHAR,
    "metadata" VARCHAR NOT NULL,
    PRIMARY KEY ("address")
);

-- ----------------------------
-- Table structure for history
-- ----------------------------
DROP TABLE IF EXISTS "history";
CREATE TABLE "history"
(
    "id"                 INTEGER PRIMARY KEY AUTOINCREMENT,
    "banAt"              TIMESTAMP        NOT NULL,
    "unbanAt"            TIMESTAMP        NOT NULL,
    "ip"                 VARCHAR          NOT NULL,
    "port"               INTEGER          NOT NULL,
    "peerId"             VARCHAR,
    "peerClientName"     VARCHAR,
    "peerUploaded"       BIGINT,
    "peerDownloaded"     BIGINT,
    "peerProgress"       DOUBLE PRECISION NOT NULL,
    "downloaderProgress" DOUBLE PRECISION NOT NULL,
    "torrent_id"         BIGINT           NOT NULL,
    "rule_id"            BIGINT           NOT NULL,
    "description"        VARCHAR          NOT NULL,
    "flags"              VARCHAR,
    "downloader"         VARCHAR          NOT NULL,
    "structuredData"     VARCHAR          NOT NULL DEFAULT '{}'
);

-- ----------------------------
-- Table structure for metadata
-- ----------------------------
DROP TABLE IF EXISTS "metadata";
CREATE TABLE "metadata"
(
    "key"   VARCHAR,
    "value" VARCHAR,
    PRIMARY KEY ("key")
);

-- ----------------------------
-- Table structure for modules
-- ----------------------------
DROP TABLE IF EXISTS "modules";
CREATE TABLE "modules"
(
    "id"   INTEGER PRIMARY KEY AUTOINCREMENT,
    "name" VARCHAR,
    UNIQUE ("name" ASC)
);

-- ----------------------------
-- Table structure for pcb_address
-- ----------------------------
DROP TABLE IF EXISTS "pcb_address";
CREATE TABLE "pcb_address"
(
    "id"                            INTEGER PRIMARY KEY AUTOINCREMENT,
    "ip"                            VARCHAR          NOT NULL,
    "port"                          INTEGER          NOT NULL,
    "torrentId"                     VARCHAR          NOT NULL,
    "lastReportProgress"            DOUBLE PRECISION NOT NULL,
    "lastReportUploaded"            BIGINT,
    "trackingUploadedIncreaseTotal" BIGINT,
    "rewindCounter"                 INTEGER          NOT NULL,
    "progressDifferenceCounter"     INTEGER          NOT NULL,
    "firstTimeSeen"                 TIMESTAMP        NOT NULL,
    "lastTimeSeen"                  TIMESTAMP        NOT NULL,
    "downloader"                    VARCHAR          NOT NULL,
    "banDelayWindowEndAt"           TIMESTAMP        NOT NULL,
    "fastPcbTestExecuteAt"          BIGINT           NOT NULL,
    "lastTorrentCompletedSize"      BIGINT           NOT NULL,
    UNIQUE ("ip" ASC, "port" ASC, "torrentId" ASC, "downloader" ASC)
);

-- ----------------------------
-- Table structure for pcb_range
-- ----------------------------
DROP TABLE IF EXISTS "pcb_range";
CREATE TABLE "pcb_range"
(
    "id"                            INTEGER PRIMARY KEY AUTOINCREMENT,
    "range"                         VARCHAR          NOT NULL,
    "torrentId"                     VARCHAR          NOT NULL,
    "lastReportProgress"            DOUBLE PRECISION NOT NULL,
    "lastReportUploaded"            BIGINT,
    "trackingUploadedIncreaseTotal" BIGINT,
    "rewindCounter"                 INTEGER          NOT NULL,
    "progressDifferenceCounter"     INTEGER          NOT NULL,
    "firstTimeSeen"                 TIMESTAMP        NOT NULL,
    "lastTimeSeen"                  TIMESTAMP        NOT NULL,
    "downloader"                    VARCHAR          NOT NULL,
    "banDelayWindowEndAt"           TIMESTAMP        NOT NULL,
    "fastPcbTestExecuteAt"          BIGINT           NOT NULL,
    "lastTorrentCompletedSize"      BIGINT           NOT NULL,
    UNIQUE ("range" ASC, "torrentId" ASC, "downloader" ASC)
);

-- ----------------------------
-- Table structure for peer_connection_metrics
-- ----------------------------
DROP TABLE IF EXISTS "peer_connection_metrics";
CREATE TABLE "peer_connection_metrics"
(
    "id"                           INTEGER PRIMARY KEY AUTOINCREMENT,
    "timeframeAt"                  TIMESTAMP NOT NULL,
    "downloader"                   VARCHAR   NOT NULL,
    "totalConnections"             BIGINT    NOT NULL DEFAULT 0,
    "incomingConnections"          BIGINT    NOT NULL DEFAULT 0,
    "remoteRefuseTransferToClient" BIGINT    NOT NULL DEFAULT 0,
    "remoteAcceptTransferToClient" BIGINT    NOT NULL DEFAULT 0,
    "localRefuseTransferToPeer"    BIGINT    NOT NULL DEFAULT 0,
    "localAcceptTransferToPeer"    BIGINT    NOT NULL DEFAULT 0,
    "localNotInterested"           BIGINT    NOT NULL DEFAULT 0,
    "questionStatus"               BIGINT    NOT NULL DEFAULT 0,
    "optimisticUnchoke"            BIGINT    NOT NULL DEFAULT 0,
    "fromDHT"                      BIGINT    NOT NULL DEFAULT 0,
    "fromPEX"                      BIGINT    NOT NULL DEFAULT 0,
    "fromLSD"                      BIGINT    NOT NULL DEFAULT 0,
    "fromTrackerOrOther"           BIGINT    NOT NULL DEFAULT 0,
    "rc4Encrypted"                 BIGINT    NOT NULL DEFAULT 0,
    "plainTextEncrypted"           BIGINT    NOT NULL DEFAULT 0,
    "utpSocket"                    BIGINT    NOT NULL DEFAULT 0,
    "tcpSocket"                    BIGINT    NOT NULL DEFAULT 0,
    UNIQUE ("timeframeAt" ASC, "downloader" ASC)
);

-- ----------------------------
-- Table structure for peer_connection_metrics_track
-- ----------------------------
DROP TABLE IF EXISTS "peer_connection_metrics_track";
CREATE TABLE "peer_connection_metrics_track"
(
    "id"          INTEGER PRIMARY KEY AUTOINCREMENT,
    "timeframeAt" TIMESTAMP NOT NULL,
    "downloader"  VARCHAR   NOT NULL,
    "torrent_id"  BIGINT    NOT NULL,
    "address"     VARCHAR   NOT NULL,
    "port"        INTEGER   NOT NULL,
    "peerId"      VARCHAR,
    "clientName"  VARCHAR,
    "lastFlags"   VARCHAR,
    UNIQUE ("timeframeAt" ASC, "downloader" ASC, "torrent_id" ASC, "address" ASC, "port" ASC)
);

-- ----------------------------
-- Table structure for peer_records
-- ----------------------------
DROP TABLE IF EXISTS "peer_records";
CREATE TABLE "peer_records"
(
    "id"               INTEGER PRIMARY KEY AUTOINCREMENT,
    "address"          VARCHAR   NOT NULL,
    "port"             INTEGER   NOT NULL DEFAULT 0,
    "torrent_id"       BIGINT    NOT NULL,
    "downloader"       VARCHAR   NOT NULL,
    "peerId"           VARCHAR,
    "clientName"       VARCHAR,
    "uploaded"         BIGINT    NOT NULL,
    "uploadedOffset"   BIGINT    NOT NULL,
    "uploadSpeed"      BIGINT    NOT NULL DEFAULT 0,
    "downloaded"       BIGINT    NOT NULL,
    "downloadedOffset" BIGINT    NOT NULL,
    "downloadSpeed"    BIGINT    NOT NULL DEFAULT 0,
    "lastFlags"        VARCHAR,
    "firstTimeSeen"    TIMESTAMP NOT NULL,
    "lastTimeSeen"     TIMESTAMP NOT NULL,
    UNIQUE ("address" ASC, "port" ASC, "torrent_id" ASC, "downloader" ASC)
);

-- ----------------------------
-- Table structure for rule_sub_info
-- ----------------------------
DROP TABLE IF EXISTS "rule_sub_info";
CREATE TABLE "rule_sub_info"
(
    "ruleId"     VARCHAR,
    "enabled"    BOOLEAN,
    "ruleName"   VARCHAR,
    "subUrl"     VARCHAR,
    "lastUpdate" BIGINT,
    "entCount"   INTEGER,
    PRIMARY KEY ("ruleId")
);

-- ----------------------------
-- Table structure for rule_sub_log
-- ----------------------------
DROP TABLE IF EXISTS "rule_sub_log";
CREATE TABLE "rule_sub_log"
(
    "id"         INTEGER PRIMARY KEY AUTOINCREMENT,
    "ruleId"     VARCHAR,
    "updateTime" BIGINT,
    "count"      INTEGER,
    "updateType" VARCHAR
);

-- ----------------------------
-- Table structure for rules
-- ----------------------------
DROP TABLE IF EXISTS "rules";
CREATE TABLE "rules"
(
    "id"        INTEGER PRIMARY KEY AUTOINCREMENT,
    "module_id" BIGINT  NOT NULL,
    "rule"      VARCHAR NOT NULL,
    UNIQUE ("module_id" ASC, "rule" ASC)
);

-- ----------------------------
-- Table structure for sqlite_sequence
-- ----------------------------
DROP TABLE IF EXISTS "sqlite_sequence";
CREATE TABLE "sqlite_sequence"
(
    "name",
    "seq"
);

-- ----------------------------
-- Table structure for sqlite_stat1
-- ----------------------------
DROP TABLE IF EXISTS "sqlite_stat1";
CREATE TABLE "sqlite_stat1"
(
    "tbl",
    "idx",
    "stat"
);

-- ----------------------------
-- Table structure for sqlite_stat4
-- ----------------------------
DROP TABLE IF EXISTS "sqlite_stat4";
CREATE TABLE "sqlite_stat4"
(
    "tbl",
    "idx",
    "neq",
    "nlt",
    "ndlt",
    "sample"
);

-- ----------------------------
-- Table structure for torrents
-- ----------------------------
DROP TABLE IF EXISTS "torrents";
CREATE TABLE "torrents"
(
    "id"             INTEGER PRIMARY KEY AUTOINCREMENT,
    "infoHash"       VARCHAR NOT NULL,
    "name"           VARCHAR NOT NULL,
    "size"           BIGINT  NOT NULL,
    "privateTorrent" BOOLEAN
);

-- ----------------------------
-- Table structure for traffic_journal_v3
-- ----------------------------
DROP TABLE IF EXISTS "traffic_journal_v3";
CREATE TABLE "traffic_journal_v3"
(
    "id"                               INTEGER PRIMARY KEY AUTOINCREMENT,
    "timestamp"                        BIGINT,
    "downloader"                       VARCHAR,
    "dataOverallUploadedAtStart"       BIGINT,
    "dataOverallUploaded"              BIGINT,
    "dataOverallDownloadedAtStart"     BIGINT,
    "dataOverallDownloaded"            BIGINT,
    "protocolOverallUploadedAtStart"   BIGINT,
    "protocolOverallUploaded"          BIGINT,
    "protocolOverallDownloadedAtStart" BIGINT,
    "protocolOverallDownloaded"        BIGINT,
    UNIQUE ("timestamp" ASC, "downloader" ASC)
);

-- ----------------------------
-- Auto increment value for alert
-- ----------------------------
UPDATE "sqlite_sequence"
SET seq = 2
WHERE name = 'alert';

-- ----------------------------
-- Indexes structure for table alert
-- ----------------------------
CREATE INDEX "alert_createAt_idx"
    ON "alert" (
                "createAt" ASC
        );
CREATE INDEX "alert_identifier_idx"
    ON "alert" (
                "identifier" ASC
        );
CREATE INDEX "alert_level_idx"
    ON "alert" (
                "level" ASC
        );
CREATE INDEX "alert_readAt_idx"
    ON "alert" (
                "readAt" ASC
        );

-- ----------------------------
-- Indexes structure for table banlist
-- ----------------------------
CREATE INDEX "banlist_address_idx"
    ON "banlist" (
                  "address" ASC
        );

-- ----------------------------
-- Indexes structure for table history
-- ----------------------------
CREATE INDEX "history_banAt_idx"
    ON "history" (
                  "banAt" ASC
        );
CREATE INDEX "history_downloader_idx"
    ON "history" (
                  "downloader" ASC
        );
CREATE INDEX "history_id_idx"
    ON "history" (
                  "id" ASC
        );
CREATE INDEX "history_ip_idx"
    ON "history" (
                  "ip" ASC
        );
CREATE INDEX "history_peerClientName_idx"
    ON "history" (
                  "peerClientName" ASC
        );
CREATE INDEX "history_peerId_idx"
    ON "history" (
                  "peerId" ASC
        );
CREATE INDEX "history_port_idx"
    ON "history" (
                  "port" ASC
        );
CREATE INDEX "history_rule_idx"
    ON "history" (
                  "rule_id" ASC
        );
CREATE INDEX "history_torrent_idx"
    ON "history" (
                  "torrent_id" ASC
        );

-- ----------------------------
-- Indexes structure for table metadata
-- ----------------------------
CREATE INDEX "metadata_key_idx"
    ON "metadata" (
                   "key" ASC
        );

-- ----------------------------
-- Indexes structure for table modules
-- ----------------------------
CREATE INDEX "modules_name_idx"
    ON "modules" (
                  "name" ASC
        );

-- ----------------------------
-- Indexes structure for table pcb_address
-- ----------------------------
CREATE INDEX "pcb_address_downloader_idx"
    ON "pcb_address" (
                      "downloader" ASC
        );
CREATE INDEX "pcb_address_firstTimeSeen_idx"
    ON "pcb_address" (
                      "firstTimeSeen" ASC
        );
CREATE INDEX "pcb_address_ip_idx"
    ON "pcb_address" (
                      "ip" ASC
        );
CREATE INDEX "pcb_address_lastTimeSeen_idx"
    ON "pcb_address" (
                      "lastTimeSeen" ASC
        );
CREATE INDEX "pcb_address_torrentId_idx"
    ON "pcb_address" (
                      "torrentId" ASC
        );

-- ----------------------------
-- Indexes structure for table pcb_range
-- ----------------------------
CREATE INDEX "pcb_range_downloader_idx"
    ON "pcb_range" (
                    "downloader" ASC
        );
CREATE INDEX "pcb_range_firstTimeSeen_idx"
    ON "pcb_range" (
                    "firstTimeSeen" ASC
        );
CREATE INDEX "pcb_range_lastTimeSeen_idx"
    ON "pcb_range" (
                    "lastTimeSeen" ASC
        );
CREATE INDEX "pcb_range_range_idx"
    ON "pcb_range" (
                    "range" ASC
        );
CREATE INDEX "pcb_range_torrentId_idx"
    ON "pcb_range" (
                    "torrentId" ASC
        );

-- ----------------------------
-- Indexes structure for table peer_connection_metrics
-- ----------------------------
CREATE INDEX "peer_connection_metrics_downloader_idx"
    ON "peer_connection_metrics" (
                                  "downloader" ASC
        );
CREATE INDEX "peer_connection_metrics_timeframeAt_idx"
    ON "peer_connection_metrics" (
                                  "timeframeAt" ASC
        );

-- ----------------------------
-- Indexes structure for table peer_connection_metrics_track
-- ----------------------------
CREATE INDEX "peer_connection_metrics_track_timeframeAt_idx"
    ON "peer_connection_metrics_track" (
                                        "timeframeAt" ASC
        );
CREATE INDEX "peer_connection_metrics_track_torrent_idx"
    ON "peer_connection_metrics_track" (
                                        "torrent_id" ASC
        );

-- ----------------------------
-- Indexes structure for table peer_records
-- ----------------------------
CREATE INDEX "peer_records_address_idx"
    ON "peer_records" (
                       "address" ASC
        );
CREATE INDEX "peer_records_clientName_idx"
    ON "peer_records" (
                       "clientName" ASC
        );
CREATE INDEX "peer_records_downloader_idx"
    ON "peer_records" (
                       "downloader" ASC
        );
CREATE INDEX "peer_records_firstTimeSeen_idx"
    ON "peer_records" (
                       "firstTimeSeen" ASC
        );
CREATE INDEX "peer_records_lastTimeSeen_idx"
    ON "peer_records" (
                       "lastTimeSeen" ASC
        );
CREATE INDEX "peer_records_peerId_idx"
    ON "peer_records" (
                       "peerId" ASC
        );
CREATE INDEX "peer_records_torrent_idx"
    ON "peer_records" (
                       "torrent_id" ASC
        );

-- ----------------------------
-- Indexes structure for table rule_sub_info
-- ----------------------------
CREATE INDEX "rule_sub_info_ruleId_idx"
    ON "rule_sub_info" (
                        "ruleId" ASC
        );

-- ----------------------------
-- Auto increment value for rule_sub_log
-- ----------------------------
UPDATE "sqlite_sequence"
SET seq = 2
WHERE name = 'rule_sub_log';

-- ----------------------------
-- Indexes structure for table rule_sub_log
-- ----------------------------
CREATE INDEX "rule_sub_log_id_idx"
    ON "rule_sub_log" (
                       "id" ASC
        );
CREATE INDEX "rule_sub_log_ruleId_idx"
    ON "rule_sub_log" (
                       "ruleId" ASC
        );

-- ----------------------------
-- Indexes structure for table rules
-- ----------------------------
CREATE INDEX "rules_id_idx"
    ON "rules" (
                "id" ASC
        );

-- ----------------------------
-- Auto increment value for torrents
-- ----------------------------
UPDATE "sqlite_sequence"
SET seq = 1
WHERE name = 'torrents';

-- ----------------------------
-- Indexes structure for table torrents
-- ----------------------------
CREATE INDEX "torrents_id_idx"
    ON "torrents" (
                   "id" ASC
        );
CREATE UNIQUE INDEX "torrents_infoHash_idx"
    ON "torrents" (
                   "infoHash" ASC
        );
CREATE INDEX "torrents_name_idx"
    ON "torrents" (
                   "name" ASC
        );

-- ----------------------------
-- Auto increment value for traffic_journal_v3
-- ----------------------------
UPDATE "sqlite_sequence"
SET seq = 1
WHERE name = 'traffic_journal_v3';

-- ----------------------------
-- Indexes structure for table traffic_journal_v3
-- ----------------------------
CREATE INDEX "traffic_journal_v3_downloader_idx"
    ON "traffic_journal_v3" (
                             "downloader" ASC
        );
CREATE INDEX "traffic_journal_v3_id_idx"
    ON "traffic_journal_v3" (
                             "id" ASC
        );
CREATE INDEX "traffic_journal_v3_timestamp_idx"
    ON "traffic_journal_v3" (
                             "timestamp" ASC
        );

PRAGMA
foreign_keys = true;
