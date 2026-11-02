CREATE TABLE devices (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    manufacturer VARCHAR(80),
    model VARCHAR(80),
    external_id VARCHAR(120) NOT NULL,
    connection_type VARCHAR(20) NOT NULL CHECK (connection_type IN ('BLUETOOTH', 'USB_SERIAL', 'SIMULATOR')),
    source_kind VARCHAR(20) NOT NULL CHECK (source_kind IN ('REAL', 'SIMULATED')),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sim_consistency CHECK ((connection_type = 'SIMULATOR') = (source_kind = 'SIMULATED')),
    CONSTRAINT ux_device_owner_external UNIQUE (owner_id, external_id)
);

CREATE TABLE measurements (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices (id) ON DELETE RESTRICT,
    profile_id UUID NOT NULL REFERENCES water_profiles (id) ON DELETE RESTRICT,
    ph NUMERIC(4, 2) NOT NULL CHECK (ph >= 0 AND ph <= 14),
    measured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_kind VARCHAR(20) NOT NULL CHECK (source_kind IN ('REAL', 'SIMULATED')),
    acidity VARCHAR(10) NOT NULL CHECK (acidity IN ('ACIDIC', 'NEUTRAL', 'ALKALINE')),
    range_status VARCHAR(10) NOT NULL CHECK (range_status IN ('OPTIMAL', 'CAUTION', 'CRITICAL')),
    validation_status VARCHAR(10) NOT NULL CHECK (validation_status IN ('VALID', 'SUSPECT')),
    validation_notes VARCHAR(500)
);
CREATE INDEX ix_measurements_owner_time ON measurements (owner_id, measured_at DESC);
CREATE INDEX ix_measurements_device ON measurements (device_id);
