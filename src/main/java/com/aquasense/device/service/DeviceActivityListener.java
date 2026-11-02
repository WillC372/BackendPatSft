package com.aquasense.device.service;

import com.aquasense.device.repository.DeviceRepository;
import com.aquasense.measurement.domain.MeasurementRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DeviceActivityListener {

    private final DeviceRepository devices;

    public DeviceActivityListener(DeviceRepository devices) {
        this.devices = devices;
    }

    @EventListener
    public void onMeasurementRecorded(MeasurementRecorded event) {
        devices.findByIdAndOwnerId(event.deviceId(), event.ownerId()).ifPresent(device -> {
            if (device.getLastSeenAt() == null || event.measuredAt().isAfter(device.getLastSeenAt())) {
                device.markSeen(event.measuredAt());
                devices.save(device);
            }
        });
    }
}
