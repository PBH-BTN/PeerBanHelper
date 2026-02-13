DELETE FROM peer_records
WHERE EXISTS (
    SELECT 1
    FROM peer_records pr2
    WHERE pr2.address = peer_records.address
      AND pr2.torrent_id = peer_records.torrent_id
      AND pr2.downloader = peer_records.downloader
      AND (
        pr2.last_time_seen > peer_records.last_time_seen
            OR (pr2.last_time_seen = peer_records.last_time_seen AND pr2.id > peer_records.id)
        )
);


ALTER TABLE peer_records DROP CONSTRAINT IF EXISTS idx_peer_records_unique;
ALTER TABLE peer_records ADD CONSTRAINT idx_peer_records_unique UNIQUE (address, torrent_id, downloader);