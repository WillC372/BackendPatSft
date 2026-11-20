-- The pH alarm can also reach the person's phone as a text message (SMS) through Twilio, sent to users.phone_number
-- (added in V29). This is whether the person wants it. Additive: existing accounts keep push and email as before and,
-- as they have no number yet, receive no SMS until they save one.
ALTER TABLE user_settings ADD COLUMN sms_alerts BOOLEAN NOT NULL DEFAULT TRUE;
