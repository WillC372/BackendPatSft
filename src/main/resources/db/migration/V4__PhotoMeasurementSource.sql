ALTER TABLE measurements DROP CONSTRAINT ck_measurement_source;
ALTER TABLE measurements ADD CONSTRAINT ck_measurement_source CHECK (source IN ('REAL', 'SIMULATED', 'MANUAL', 'PHOTO'));
