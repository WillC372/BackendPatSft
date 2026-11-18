package com.aquaassistant.dashboard;

import com.aquaassistant.alert.AlertResponse;
import com.aquaassistant.device.dto.DeviceStatusResponse;
import com.aquaassistant.measurement.dto.MeasurementResponse;
import com.aquaassistant.profile.dto.ProfileResponse;
import java.util.List;

public record DashboardResponse(String fullName, String plan, MeasurementResponse latest, ProfileResponse activeProfile,
                                DeviceStatusResponse device, long unreadAlerts, List<AlertResponse> recentAlerts,
                                long measurementCount, String tip, String disclaimer,
                                com.aquaassistant.place.dto.PlaceDtos.PlaceResponse place,
                                java.util.List<com.aquaassistant.place.ParameterGuide.Reading> parameters) {
}
