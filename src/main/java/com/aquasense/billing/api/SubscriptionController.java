package com.aquasense.billing.api;

import com.aquasense.billing.api.SubscriptionDtos.AssignRequest;
import com.aquasense.billing.api.SubscriptionDtos.SubscriptionResponse;
import com.aquasense.billing.domain.PlanLimits;
import com.aquasense.billing.domain.PlanType;
import com.aquasense.billing.domain.Subscription;
import com.aquasense.billing.service.SubscriptionService;
import com.aquasense.billing.service.UsageService;
import com.aquasense.identity.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Suscripciones")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptions;
    private final UsageService usage;
    private final com.aquasense.reporting.service.ReportQuota reportQuota;

    public SubscriptionController(SubscriptionService subscriptions, UsageService usage,
                                  com.aquasense.reporting.service.ReportQuota reportQuota) {
        this.subscriptions = subscriptions;
        this.usage = usage;
        this.reportQuota = reportQuota;
    }

    @GetMapping("/me")
    public SubscriptionResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return toResponse(user.id(), subscriptions.current(user.id()));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionResponse assign(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID userId,
                                       @Valid @RequestBody AssignRequest request) {
        Subscription s = subscriptions.assign(admin.id(), userId, request.plan(), request.days());
        return toResponse(userId, s);
    }

    private SubscriptionResponse toResponse(UUID ownerId, Subscription s) {
        PlanType effective = s.effectivePlan(Instant.now());
        return new SubscriptionResponse(effective, s.getStatus(), s.getCurrentPeriodEnd(), PlanLimits.of(effective),
                usage.of(ownerId, reportQuota.usedThisMonth(ownerId)), false, SubscriptionDtos.PAYMENTS_NOTICE);
    }
}
