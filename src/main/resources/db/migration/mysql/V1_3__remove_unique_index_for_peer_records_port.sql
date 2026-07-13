DELETE pr1
FROM peer_records pr1
JOIN peer_records pr2
  ON pr2.address = pr1.address
 AND pr2.torrent_id = pr1.torrent_id
 AND pr2.downloader = pr1.downloader
 AND (
      pr2.last_time_seen > pr1.last_time_seen
      OR (
          pr2.last_time_seen = pr1.last_time_seen
          AND pr2.id > pr1.id
      )
 );

ALTER TABLE peer_records DROP CONSTRAINT IF EXISTS idx_peer_records_unique;
ALTER TABLE peer_records ADD CONSTRAINT idx_peer_records_unique UNIQUE (address, torrent_id, downloader);