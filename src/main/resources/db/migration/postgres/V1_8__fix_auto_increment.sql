-- 真的给修力竭了，我吐了

SELECT setval(
               pg_get_serial_sequence('torrents', 'id'),
               COALESCE((SELECT MAX(id) FROM torrents), 1),
               true
       );

SELECT setval(
               pg_get_serial_sequence('history', 'id'),
               COALESCE((SELECT MAX(id) FROM history), 1),
               true
       );

SELECT setval(
               pg_get_serial_sequence('peer_records', 'id'),
               COALESCE((SELECT MAX(id) FROM peer_records), 1),
               true
       );

SELECT setval(
               pg_get_serial_sequence('traffic_journal_v3', 'id'),
               COALESCE((SELECT MAX(id) FROM traffic_journal_v3), 1),
               true
       );

SELECT setval(
               pg_get_serial_sequence('rule_sub_log', 'id'),
               COALESCE((SELECT MAX(id) FROM rule_sub_log), 1),
               true
       );

SELECT setval(
               pg_get_serial_sequence('peer_connection_metrics', 'id'),
               COALESCE((SELECT MAX(id) FROM peer_connection_metrics), 1),
               true
       );

SELECT setval(
               pg_get_serial_sequence('peer_connection_metrics_track', 'id'),
               COALESCE((SELECT MAX(id) FROM peer_connection_metrics_track), 1),
               true
       );

SELECT setval(
               pg_get_serial_sequence('alert', 'id'),
               COALESCE((SELECT MAX(id) FROM alert), 1),
               true
       );