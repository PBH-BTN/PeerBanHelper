-- Add indexes for history table traffic columns
CREATE INDEX idx_history_uploaded ON history (peer_uploaded DESC);
CREATE INDEX idx_history_downloaded ON history (peer_downloaded DESC);
