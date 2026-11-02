package com.aquasense.billing.service;

import com.aquasense.billing.domain.PlanLimits;
import com.aquasense.billing.domain.PlanType;
import com.aquasense.billing.domain.Subscription;
import com.aquasense.billing.repository.SubscriptionRepository;
import com.aquasense.identity.repository.UserRepository;
import com.aquasense.shared.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final UserRepository users;

    public SubscriptionService(SubscriptionRepository subscriptions, UserRepository users) {
        this.subscriptions = subscriptions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PlanType effectivePlan(UUID ownerId) {
        return subscriptions.findByOwnerId(ownerId).map(s -> s.effectivePlan(Instant.now())).orElse(PlanType.FREE);
    }

    @Transactional(readOnly = true)
    public PlanLimits limitsFor(UUID ownerId) {
        return PlanLimits.of(effectivePlan(ownerId));
    }

    @Transactional(readOnly = true)
    public Subscription current(UUID ownerId) {
        return subscriptions.findByOwnerId(ownerId).orElseGet(() -> new Subscription(ownerId));
    }

    @Transactional
    public Subscription assign(UUID adminId, UUID targetUserId, PlanType plan, Integer days) {
        if (!users.existsById(targetUserId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "El usuario no existe.");
        }
        if (plan == PlanType.PREMIUM && (days == null || days < 1 || days > 366)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_DURATION",
                    "Indique una duración de 1 a 366 días para el plan Premium.");
        }
        Subscription subscription = subscriptions.findByOwnerId(targetUserId)
                .orElseGet(() -> new Subscription(targetUserId));
        Instant now = Instant.now();
        subscription.assign(plan, plan == PlanType.PREMIUM ? now.plus(Duration.ofDays(days)) : null, adminId, now);
        return subscriptions.save(subscription);
    }
}
