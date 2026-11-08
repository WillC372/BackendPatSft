package com.aquasense.dashboard;

import com.aquasense.alert.AlertService;
import com.aquasense.common.BusinessException;
import com.aquasense.device.DeviceService;
import com.aquasense.intelligence.IntelligenceFacade;
import com.aquasense.measurement.MeasurementRepository;
import com.aquasense.measurement.MeasurementService;
import com.aquasense.profile.ProfileService;
import com.aquasense.profile.WaterProfile;
import com.aquasense.profile.dto.ProfileResponse;
import com.aquasense.security.CurrentUser;
import com.aquasense.user.User;
import com.aquasense.user.UserRepository;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read query for the dashboard: gathers in a single response everything the home screen needs.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final UserRepository userRepository;
    private final MeasurementService measurementService;
    private final MeasurementRepository measurementRepository;
    private final ProfileService profileService;
    private final DeviceService deviceService;
    private final AlertService alertService;

    public DashboardController(UserRepository userRepository, MeasurementService measurementService,
                               MeasurementRepository measurementRepository, ProfileService profileService,
                               DeviceService deviceService, AlertService alertService) {
        this.userRepository = userRepository;
        this.measurementService = measurementService;
        this.measurementRepository = measurementRepository;
        this.profileService = profileService;
        this.deviceService = deviceService;
        this.alertService = alertService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Long userId = CurrentUser.id();
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("La cuenta no existe."));
        WaterProfile profile = profileService.activeProfile(userId);
        String tip = String.format(Locale.US, "Para %s, mantén el pH entre %.1f y %.1f.", profile.getName(),
            profile.getIdealMin(), profile.getIdealMax());
        return new DashboardResponse(user.getFullName(), user.getPlan().name(),
            measurementService.latest(userId).orElse(null), ProfileResponse.from(profile, profile.getId()),
            deviceService.status(userId), alertService.unreadCount(userId), alertService.list(userId, true, 3),
            measurementRepository.countByUserId(userId), tip, IntelligenceFacade.PH_DISCLAIMER);
    }
}
