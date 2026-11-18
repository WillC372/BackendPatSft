-- Aqua reads its answers aloud unless the person turns it off.
ALTER TABLE user_settings ADD COLUMN voice_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Translations of the interface, shared by everyone: each Spanish text is translated once per language with the AI
-- and kept here, so the next person who chooses that language gets it at once.
CREATE TABLE ui_translations (
    id           VARCHAR(80)    PRIMARY KEY,
    lang         VARCHAR(12)    NOT NULL,
    source_text  VARCHAR(2000)  NOT NULL,
    target_text  VARCHAR(4000)  NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_ui_translations_lang ON ui_translations (lang);
