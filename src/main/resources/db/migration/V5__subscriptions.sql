CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan VARCHAR(10) NOT NULL CHECK (plan IN ('FREE', 'PREMIUM')),
    status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELED', 'EXPIRED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end TIMESTAMPTZ,
    source VARCHAR(20) NOT NULL CHECK (source IN ('ADMIN')),
    granted_by UUID REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ux_subscription_owner UNIQUE (owner_id)
);
