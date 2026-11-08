-- Los tokens enviados por correo tienen un propósito: recuperar contraseña o confirmar la cuenta de administrador.
ALTER TABLE password_reset_tokens ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'PASSWORD_RESET';
ALTER TABLE password_reset_tokens ADD CONSTRAINT ck_token_purpose CHECK (purpose IN ('PASSWORD_RESET', 'ADMIN_VERIFY'));
CREATE INDEX ix_reset_tokens_user_purpose ON password_reset_tokens (user_id, purpose);

-- Ortografía de las descripciones de los perfiles del sistema.
UPDATE water_profiles SET description = 'Agua destinada al consumo humano. El pH por sí solo no demuestra que el agua sea potable.'
WHERE system_profile = TRUE AND type = 'DRINKING';
UPDATE water_profiles SET description = 'Acuario comunitario de agua dulce. Cada especie tiene su propio rango; ajústalo en un perfil personalizado.'
WHERE system_profile = TRUE AND type = 'AQUARIUM';
