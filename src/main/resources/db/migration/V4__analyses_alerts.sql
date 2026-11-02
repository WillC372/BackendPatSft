CREATE TABLE analyses (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES water_profiles (id) ON DELETE RESTRICT,
    period_from TIMESTAMPTZ,
    period_to TIMESTAMPTZ,
    measurement_count INTEGER NOT NULL,
    sufficient_data BOOLEAN NOT NULL,
    report_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_analyses_owner_time ON analyses (owner_id, created_at DESC);

CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses (id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    severity VARCHAR(10) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    measured_at TIMESTAMPTZ NOT NULL,
    ph NUMERIC(4, 2) NOT NULL,
    message VARCHAR(300) NOT NULL,
    explanation VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    CONSTRAINT ux_alert_dedup UNIQUE (owner_id, type, measured_at)
);
CREATE INDEX ix_alerts_owner_open ON alerts (owner_id, created_at DESC) WHERE acknowledged_at IS NULL;
