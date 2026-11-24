CREATE INDEX idx_history_ip_prefix ON history (HOST(ip) text_pattern_ops);
