-- The light theme is now the default. Every account created so far got the dark theme automatically (it was the old
-- default), so they all start in light; whoever prefers dark can choose it again in Configuración.
UPDATE user_settings SET theme = 'LIGHT' WHERE theme = 'DARK';
