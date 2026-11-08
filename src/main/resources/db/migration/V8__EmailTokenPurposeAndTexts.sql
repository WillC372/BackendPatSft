-- Tokens sent by email have a purpose: password recovery or confirming the administrator account.
ALTER TABLE password_reset_tokens ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'PASSWORD_RESET';
ALTER TABLE password_reset_tokens ADD CONSTRAINT ck_token_purpose CHECK (purpose IN ('PASSWORD_RESET', 'ADMIN_VERIFY'));
CREATE INDEX ix_reset_tokens_user_purpose ON password_reset_tokens (user_id, purpose);

-- Spelling of the system profile descriptions.
UPDATE water_profiles SET description = 'Agua destinada al consumo humano. El pH por sí solo no demuestra que el agua sea potable.'
WHERE system_profile = TRUE AND type = 'DRINKING';
UPDATE water_profiles SET description = 'Acuario comunitario de agua dulce. Cada especie tiene su propio rango; ajústalo en un perfil personalizado.'
WHERE system_profile = TRUE AND type = 'AQUARIUM';
