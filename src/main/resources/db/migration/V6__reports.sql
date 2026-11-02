CREATE TABLE reports (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES water_profiles (id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL,
    format VARCHAR(10) NOT NULL CHECK (format IN ('MARKDOWN', 'HTML')),
    period_from TIMESTAMPTZ,
    period_to TIMESTAMPTZ,
    measurement_count INTEGER NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reports_owner_time ON reports (owner_id, created_at DESC);
