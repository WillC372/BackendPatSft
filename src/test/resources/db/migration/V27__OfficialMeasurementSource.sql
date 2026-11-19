-- Official readings of a USGS station loaded into a place (source OFFICIAL, "Dato oficial del USGS"). They are real
-- measurements of a river taken by the U.S. Geological Survey, not the person's own: the app labels them as such.
-- They let the analyses (diagnosis, anomalies, machine learning, the LSTM network) work with days of real data.
ALTER TABLE measurements DROP CONSTRAINT ck_measurement_source;
ALTER TABLE measurements ADD CONSTRAINT ck_measurement_source CHECK (source IN ('REAL', 'SIMULATED', 'MANUAL', 'PHOTO', 'OFFICIAL'));
