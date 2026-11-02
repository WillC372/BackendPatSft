package com.aquasense.billing.api;

import com.aquasense.billing.domain.PlanLimits;
import com.aquasense.billing.domain.PlanType;
import com.aquasense.billing.domain.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class SubscriptionDtos {

    public static final String PAYMENTS_NOTICE = "El cobro en línea todavía no está habilitado: el plan Premium lo "
            + "asigna un administrador. Los pagos reales requieren las credenciales de la pasarela.";

    private SubscriptionDtos() {
    }

    public record Usage(long devices, long customProfiles, long analysesToday, long reportsThisMonth) {
    }

    public record SubscriptionResponse(PlanType plan, SubscriptionStatus status, Instant currentPeriodEnd,
                                       PlanLimits limits, Usage usage, boolean onlinePaymentsAvailable,
                                       String notice) {
    }

    public record AssignRequest(@NotNull PlanType plan, Integer days) {
    }
}
