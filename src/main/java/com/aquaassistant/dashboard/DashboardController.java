package com.aquaassistant.dashboard;

import com.aquaassistant.alert.AlertService;
import com.aquaassistant.common.BusinessException;
import com.aquaassistant.device.DeviceService;
import com.aquaassistant.intelligence.IntelligenceFacade;
import com.aquaassistant.measurement.MeasurementRepository;
import com.aquaassistant.measurement.MeasurementService;
import com.aquaassistant.profile.ProfileService;
import com.aquaassistant.profile.WaterProfile;
import com.aquaassistant.profile.dto.ProfileResponse;
import com.aquaassistant.security.CurrentUser;
import com.aquaassistant.user.User;
import com.aquaassistant.user.UserRepository;
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
    private final com.aquaassistant.place.PlaceAccess places;
    private final com.aquaassistant.place.PlaceService placeService;

    public DashboardController(UserRepository userRepository, MeasurementService measurementService,
                               MeasurementRepository measurementRepository, ProfileService profileService,
                               DeviceService deviceService, AlertService alertService,
                               com.aquaassistant.place.PlaceAccess places,
                               com.aquaassistant.place.PlaceService placeService) {
        this.places = places;
        this.placeService = placeService;
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
        Long placeId = places.activeId(userId);
        com.aquaassistant.place.dto.PlaceDtos.PlaceResponse place = placeService.get(userId, placeId);
        String tip = String.format(Locale.US, "Para %s, mantén el pH entre %.1f y %.1f.", profile.getName(),
            profile.getIdealMin(), profile.getIdealMax());
        return new DashboardResponse(user.getFullName(), user.getPlan().name(),
            measurementService.latest(userId).orElse(null), ProfileResponse.from(profile, profile.getId()),
            deviceService.status(userId), alertService.unreadCount(userId), alertService.list(userId, true, 3),
            measurementRepository.countByPlaceId(placeId), tip, IntelligenceFacade.PH_DISCLAIMER, place,
            com.aquaassistant.place.ParameterGuide.latest(com.aquaassistant.place.PlaceKind.valueOf(place.kind()),
                measurementRepository.findTop50ByPlaceIdOrderByMeasuredAtDesc(placeId)));
    }
}
