package com.aquasense.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(nullable = false)
    private String source = "ADMIN";

    @Column(name = "granted_by")
    private UUID grantedBy;

    protected Subscription() {
    }

    public Subscription(UUID ownerId) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.plan = PlanType.FREE;
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void assign(PlanType newPlan, Instant periodEnd, UUID adminId, Instant now) {
        this.plan = newPlan;
        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = now;
        this.currentPeriodEnd = newPlan == PlanType.PREMIUM ? periodEnd : null;
        this.grantedBy = adminId;
    }

    public PlanType effectivePlan(Instant now) {
        boolean premiumActive = plan == PlanType.PREMIUM && status == SubscriptionStatus.ACTIVE
                && (currentPeriodEnd == null || currentPeriodEnd.isAfter(now));
        return premiumActive ? PlanType.PREMIUM : PlanType.FREE;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public PlanType getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }
}
