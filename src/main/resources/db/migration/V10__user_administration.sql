-- Administración de usuarios: cuentas desactivadas por un administrador y fecha del último inicio de sesión.
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN disabled_reason VARCHAR(300);
ALTER TABLE users ADD COLUMN disabled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX ix_users_created_at ON users (created_at);
CREATE INDEX ix_audit_action_time ON audit_log (action, created_at);
