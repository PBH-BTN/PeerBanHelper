WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY address, torrent_id, downloader
                                ORDER BY last_time_seen DESC, id DESC) AS rn
    FROM peer_records
)
DELETE FROM peer_records pr
    USING ranked r
WHERE pr.id = r.id
  AND r.rn > 1;


ALTER TABLE peer_records DROP CONSTRAINT IF EXISTS idx_peer_records_unique;
ALTER TABLE peer_records ADD CONSTRAINT idx_peer_records_unique UNIQUE (address, torrent_id, downloader);