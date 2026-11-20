-- Mobile number of the account (E.164, e.g. +573001234567), to send the pH alarms by WhatsApp, and whether the
-- person wants them. Additive: existing accounts have no number and keep receiving push and email as before.
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20);
ALTER TABLE user_settings ADD COLUMN whatsapp_alerts BOOLEAN NOT NULL DEFAULT TRUE;
