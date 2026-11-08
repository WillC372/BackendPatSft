-- Aligns the system profiles with the official texts of the knowledge base (knowledge/guidelines.json).
-- Measurements already stored keep the status they were classified with at the time.

-- CDC, MAHC 2024: 5.7.3.4.1 requires 7.0-7.8; 6.6.3.1 considers a pH below 6.5 or above 8.0 an imminent hazard.
UPDATE water_profiles SET ideal_min = 7.0, ideal_max = 7.8, caution_min = 6.5, caution_max = 8.0,
    description = 'Piscinas y recintos acuáticos. Fuera de 7.0-7.8 baja la eficacia del desinfectante; por debajo de 6.5 o por encima de 8.0 la CDC lo considera un riesgo inminente para la salud.',
    source = 'CDC, Model Aquatic Health Code 2024 (5.ª ed.): 5.7.3.4.1 rango 7.0-7.8; 6.6.3.1 riesgo inminente con pH menor de 6.5 o mayor de 8.0.'
WHERE system_profile = TRUE AND type = 'POOL';

-- FAO, Water quality for agriculture: normal range 6.5-8.4 (section 5.2 and table 1); usual range 6.0-8.5 (table 2).
UPDATE water_profiles SET ideal_min = 6.5, ideal_max = 8.4, caution_min = 6.0, caution_max = 8.5,
    description = 'Agua de riego. Un pH fuera del rango normal es una alerta para evaluar el agua: puede indicar un desequilibrio nutricional o un ion tóxico y daña los equipos de riego.',
    source = 'FAO, Water quality for agriculture (Ayers y Westcot, 1985): rango normal 6.5-8.4 (sección 5.2 y tabla 1); rango usual 6.0-8.5 (tabla 2).'
WHERE system_profile = TRUE AND type = 'IRRIGATION';

UPDATE water_profiles SET
    source = 'EPA, Secondary Drinking Water Standards: 6.5-8.5, guía no obligatoria por sabor, corrosión y depósitos. La OMS (2022) no fija un valor guía de salud para el pH y lo considera un parámetro operacional.'
WHERE system_profile = TRUE AND type = 'DRINKING';

UPDATE water_profiles SET
    source = 'Referencia general: estándar secundario de la EPA (6.5-8.5, no obligatorio). La OMS no fija un valor guía de salud para el pH. Verificar la norma local.'
WHERE system_profile = TRUE AND type = 'GENERAL';

UPDATE water_profiles SET
    source = 'Sin norma oficial verificada en la base de conocimiento de AquaSense. Rango orientativo para acuarios comunitarios; ajustarlo a cada especie.'
WHERE system_profile = TRUE AND type = 'AQUARIUM';
