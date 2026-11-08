package com.aquasense.dashboard;

import com.aquasense.alert.AlertResponse;
import com.aquasense.device.dto.DeviceStatusResponse;
import com.aquasense.measurement.dto.MeasurementResponse;
import com.aquasense.profile.dto.ProfileResponse;
import java.util.List;

public record DashboardResponse(String fullName, String plan, MeasurementResponse latest, ProfileResponse activeProfile,
                                DeviceStatusResponse device, long unreadAlerts, List<AlertResponse> recentAlerts,
                                long measurementCount, String tip, String disclaimer) {
}
