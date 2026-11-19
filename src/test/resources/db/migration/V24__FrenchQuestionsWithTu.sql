-- French questions written with the inverted "vous" ("Que souhaitez-vous ajuster ?") escaped the earlier cleanup:
-- they are translated again with "tu" (the AI is asked again when a translation uses "vous").
DELETE FROM ui_translations WHERE lang = 'fr' AND (LOWER(target_text) LIKE '%-vous%' OR LOWER(target_text) LIKE '%vous %'
    OR LOWER(target_text) LIKE '%vous.%' OR LOWER(target_text) LIKE '%votre%' OR LOWER(target_text) LIKE '% vos %');
