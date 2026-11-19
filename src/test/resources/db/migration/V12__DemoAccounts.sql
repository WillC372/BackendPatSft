-- Demonstration accounts (one per role of the case study). They are real accounts with limits: they cannot be
-- deleted, change their password or pay, and the demonstration administrator can only look.
ALTER TABLE users ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
