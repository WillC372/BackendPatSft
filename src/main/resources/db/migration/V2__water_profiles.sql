CREATE TABLE water_profiles (
    id UUID PRIMARY KEY,
    owner_id UUID REFERENCES users (id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('DRINKING', 'POOL', 'AQUARIUM', 'AGRICULTURE', 'GENERAL', 'CUSTOM')),
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    optimal_min NUMERIC(4, 2) NOT NULL,
    optimal_max NUMERIC(4, 2) NOT NULL,
    caution_min NUMERIC(4, 2) NOT NULL,
    caution_max NUMERIC(4, 2) NOT NULL,
    source_reference VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_profile_ranges CHECK (
        caution_min >= 0 AND caution_max <= 14
        AND caution_min <= optimal_min AND optimal_min < optimal_max AND optimal_max <= caution_max),
    CONSTRAINT ck_system_type CHECK (owner_id IS NOT NULL OR type <> 'CUSTOM')
);
CREATE INDEX ix_water_profiles_owner ON water_profiles (owner_id);

INSERT INTO water_profiles (id, owner_id, type, name, description, optimal_min, optimal_max, caution_min, caution_max, source_reference) VALUES
('00000000-0000-0000-0000-000000000001', NULL, 'DRINKING', 'Agua potable',
 'Rango operativo habitual para agua de consumo. El pH por sí solo no demuestra que el agua sea potable ni segura.',
 6.50, 8.50, 6.00, 9.00, 'OMS, Guidelines for Drinking-water Quality (pH operativo 6,5–8,5); EPA, estándar secundario 6,5–8,5. Banda de precaución definida por AquaSense.'),
('00000000-0000-0000-0000-000000000002', NULL, 'POOL', 'Piscina',
 'Rango recomendado para agua de piscina con desinfección.',
 7.20, 7.80, 6.80, 8.20, 'CDC, Model Aquatic Health Code (pH 7,2–7,8). Banda de precaución definida por AquaSense.'),
('00000000-0000-0000-0000-000000000003', NULL, 'AQUARIUM', 'Acuario de agua dulce',
 'Rango general para acuarios comunitarios de agua dulce. Cada especie tiene requisitos propios: use un perfil personalizado.',
 6.50, 7.50, 6.00, 8.00, 'Rango general de acuariofilia de agua dulce; verifique las necesidades de sus especies. Banda de precaución definida por AquaSense.'),
('00000000-0000-0000-0000-000000000004', NULL, 'AGRICULTURE', 'Agricultura y riego',
 'Rango normal para agua de riego.',
 6.50, 8.40, 6.00, 9.00, 'FAO, Irrigation and Drainage Paper 29 (pH normal 6,5–8,4). Banda de precaución definida por AquaSense.'),
('00000000-0000-0000-0000-000000000005', NULL, 'GENERAL', 'Análisis general',
 'Referencia general cuando no se aplica un contexto específico.',
 6.50, 8.50, 6.00, 9.00, 'Rango operativo general 6,5–8,5. Banda de precaución definida por AquaSense.');
