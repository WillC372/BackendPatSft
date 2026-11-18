-- The shared dictionary of translations is public: it must not keep anything a person wrote or anything about them.
-- The app no longer sends those texts (names, e-mails, places, notes, chat questions are marked translate="no", and the
-- server refuses texts with an e-mail address); this removes what was stored before.
DELETE FROM ui_translations WHERE source_text LIKE '%_@_%._%';
DELETE FROM ui_translations WHERE source_text LIKE 'Tu cuenta: %' OR source_text LIKE 'Lugar: %. Cambiar de lugar'
    OR source_text LIKE 'Gestionar %' OR source_text LIKE 'Quitar a %' OR source_text LIKE 'Escribe % para confirmar'
    OR source_text LIKE 'Compartir "%"' OR source_text LIKE '% (se desplaza)';
DELETE FROM ui_translations WHERE source_text IN (SELECT full_name FROM users)
    OR source_text IN (SELECT name FROM places WHERE name <> 'Mi agua')
    OR source_text IN (SELECT name FROM water_profiles WHERE NOT system_profile)
    OR source_text IN (SELECT title FROM reminders WHERE kind = 'CUSTOM')
    OR source_text IN (SELECT note FROM treatments WHERE note IS NOT NULL)
    OR source_text IN (SELECT name FROM devices)
    OR source_text IN (SELECT content FROM chat_messages WHERE role = 'USER')
    OR source_text IN (SELECT detail FROM audit_log WHERE detail IS NOT NULL);
-- Names of the temporary test accounts used to check the translations (already deleted).
DELETE FROM ui_translations WHERE source_text IN ('Prueba Cambio', 'Prueba Idioma', 'Prueba Verifica', 'Laura Gómez',
    'Prueba corta tres de hoy');
-- The box of the browser's own translator ("Valora esta traducción"...) is not part of the app.
DELETE FROM ui_translations WHERE source_text IN ('Valora esta traducción', 'Buena traducción', 'Mala traducción', 'Texto original',
    'Tu opinión servirá para ayudar a mejorar el Traductor de Google');
