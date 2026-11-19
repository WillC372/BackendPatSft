-- Some answers of the AI came back with the Spanish text unchanged ("¿Qué quieres ajustar?" stored as its Italian
-- translation), and the app showed them in Spanish for good. They are removed so they are translated again; the AI
-- service no longer keeps a translation identical to a Spanish source.
DELETE FROM ui_translations
WHERE lang <> 'es'
  AND LOWER(TRIM(target_text)) = LOWER(TRIM(source_text))
  AND source_text <> 'Español'
  AND (LOWER(source_text) LIKE '%ñ%' OR source_text LIKE '%¿%' OR source_text LIKE '%¡%'
    OR LOWER(source_text) LIKE '%á%' OR LOWER(source_text) LIKE '%é%' OR LOWER(source_text) LIKE '%í%'
    OR LOWER(source_text) LIKE '%ó%' OR LOWER(source_text) LIKE '%ú%'
    OR LOWER(' ' || source_text || ' ') LIKE '% el %' OR LOWER(' ' || source_text || ' ') LIKE '% los %'
    OR LOWER(' ' || source_text || ' ') LIKE '% las %' OR LOWER(' ' || source_text || ' ') LIKE '% del %'
    OR LOWER(' ' || source_text || ' ') LIKE '% que %' OR LOWER(' ' || source_text || ' ') LIKE '% tu %'
    OR LOWER(' ' || source_text || ' ') LIKE '% tus %' OR LOWER(' ' || source_text || ' ') LIKE '% agua %'
    OR LOWER(' ' || source_text || ' ') LIKE '% medidor %');
