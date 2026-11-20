-- Some answers of the AI were rewritten in Spanish instead of translated: «¿Qué ha aplicado o ha hecho?» and
-- «Ácido cianúrico» stored as Italian, «estación» and «Cerrar sesión» inside Japanese, «relatório» (Portuguese) in
-- Italian, and the old label «Resumen en español» kept in English. The app showed them that way for good. They are
-- removed so they are translated again; the AI service now asks once more and never keeps a translation with ¿, ¡ or
-- a word with ñ, á, í, ó or ú (letters that among the app's languages only Spanish uses) unless it is a name copied
-- from the source. Only the shared dictionary of the interface is touched (ui_translations), never user data.
DELETE FROM ui_translations
WHERE lang <> 'es'
  AND source_text <> 'Español'  -- the language selector shows each language by its own name
  AND (target_text LIKE '%¿%' OR target_text LIKE '%¡%'
    OR target_text LIKE '%ñ%' OR target_text LIKE '%Ñ%'
    OR target_text LIKE '%á%' OR target_text LIKE '%Á%'
    OR target_text LIKE '%í%' OR target_text LIKE '%Í%'
    OR target_text LIKE '%ó%' OR target_text LIKE '%Ó%'
    OR target_text LIKE '%ú%' OR target_text LIKE '%Ú%');
