package com.aquasense.billing.service;

import com.aquasense.billing.domain.PlanLimits;
import com.aquasense.device.repository.DeviceRepository;
import com.aquasense.intelligence.repository.AnalysisRepository;
import com.aquasense.shared.DomainException;
import com.aquasense.waterprofile.repository.WaterProfileRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LimitGuard {

    public static final String CODE = "PLAN_LIMIT_REACHED";

    private final SubscriptionService subscriptions;
    private final DeviceRepository devices;
    private final WaterProfileRepository profiles;
    private final AnalysisRepository analyses;

    public LimitGuard(SubscriptionService subscriptions, DeviceRepository devices, WaterProfileRepository profiles,
                      AnalysisRepository analyses) {
        this.subscriptions = subscriptions;
        this.devices = devices;
        this.profiles = profiles;
        this.analyses = analyses;
    }

    public void requireDeviceSlot(UUID ownerId) {
        PlanLimits limits = subscriptions.limitsFor(ownerId);
        if (devices.countByOwnerId(ownerId) >= limits.maxDevices()) {
            throw denied("Su plan permite hasta " + limits.maxDevices() + " dispositivos vinculados.");
        }
    }

    public void requireProfileSlot(UUID ownerId) {
        PlanLimits limits = subscriptions.limitsFor(ownerId);
        if (profiles.countByOwnerId(ownerId) >= limits.maxCustomProfiles()) {
            throw denied("Su plan permite hasta " + limits.maxCustomProfiles() + " perfiles personalizados.");
        }
    }

    public void requireAnalysisQuota(UUID ownerId) {
        PlanLimits limits = subscriptions.limitsFor(ownerId);
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        if (analyses.countByOwnerIdAndCreatedAtAfter(ownerId, startOfDay) >= limits.maxAnalysesPerDay()) {
            throw denied("Alcanzó el máximo de " + limits.maxAnalysesPerDay() + " análisis por día de su plan.");
        }
    }

    public static DomainException denied(String detail) {
        return new DomainException(HttpStatus.PAYMENT_REQUIRED, CODE,
                detail + " Actualice a Premium para ampliar sus límites.");
    }
}
