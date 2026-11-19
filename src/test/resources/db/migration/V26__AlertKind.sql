-- Which rule created each alert. The app sounds an alarm and Aqua speaks for an alteration of the pH
-- (kind PH_ALTERATION), and the same kind of alert is not repeated in the same place for 30 minutes.
-- Older alerts keep it empty.
ALTER TABLE alerts ADD COLUMN kind VARCHAR(30);
CREATE INDEX ix_alerts_user_kind_time ON alerts (user_id, kind, created_at);
