-- Profile photo. In its own table so the users row, read on every request, does not carry the image.
CREATE TABLE user_avatars (
    user_id    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    image      BYTEA                    NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
-- Version of the photo (its time), so the client knows when to download it again; null without photo.
ALTER TABLE users ADD COLUMN avatar_updated_at TIMESTAMP WITH TIME ZONE;
