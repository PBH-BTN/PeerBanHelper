WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY address, torrent_id, downloader
                            ORDER BY last_time_seen DESC, id DESC) AS rn
    FROM peer_records
)
DELETE FROM peer_records
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);


DROP INDEX IF EXISTS idx_peer_records_unique;
CREATE UNIQUE INDEX idx_peer_records_unique ON peer_records (address, torrent_id, downloader);