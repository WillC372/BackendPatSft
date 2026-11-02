package com.aquasense.billing.service;

import com.aquasense.billing.api.SubscriptionDtos.Usage;
import com.aquasense.device.repository.DeviceRepository;
import com.aquasense.intelligence.repository.AnalysisRepository;
import com.aquasense.waterprofile.repository.WaterProfileRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageService {

    private final DeviceRepository devices;
    private final WaterProfileRepository profiles;
    private final AnalysisRepository analyses;

    public UsageService(DeviceRepository devices, WaterProfileRepository profiles, AnalysisRepository analyses) {
        this.devices = devices;
        this.profiles = profiles;
        this.analyses = analyses;
    }

    @Transactional(readOnly = true)
    public Usage of(UUID ownerId, long reportsThisMonth) {
        return new Usage(devices.countByOwnerId(ownerId), profiles.countByOwnerId(ownerId),
                analyses.countByOwnerIdAndCreatedAtAfter(ownerId, Instant.now().truncatedTo(ChronoUnit.DAYS)),
                reportsThisMonth);
    }
}
