-- Translations made before the form-of-address check (TranslationService.OTHER_TONE) are translated again.
-- The same tone in every text: the few translations that used another one are translated again (UiTextWarmer).
DELETE FROM ui_translations WHERE lang = 'fr' AND (target_text LIKE 'Vous %' OR target_text LIKE '% vous %' OR target_text LIKE '% vous.'
    OR target_text LIKE 'Votre %' OR target_text LIKE '% votre %' OR target_text LIKE '% vos %' OR target_text LIKE 'Vos %');
DELETE FROM ui_translations WHERE lang = 'de' AND (target_text LIKE 'Sie %' OR target_text LIKE '% Sie %' OR target_text LIKE '% Sie.'
    OR target_text LIKE '% Ihr%' OR target_text LIKE 'Ihr%' OR target_text LIKE '% Ihnen%');
DELETE FROM ui_translations WHERE lang = 'it' AND (target_text LIKE '% Lei %' OR target_text LIKE '% Suo %' OR target_text LIKE '% Sua %');
DELETE FROM ui_translations WHERE lang = 'ja' AND (target_text LIKE '%てね%' OR target_text LIKE '%ね' OR target_text LIKE '%ね。'
    OR target_text LIKE '%よ' OR target_text LIKE '%よ。' OR target_text LIKE '%だよ%');
DELETE FROM ui_translations WHERE lang = 'zh-Hans' AND target_text LIKE '%您%';
