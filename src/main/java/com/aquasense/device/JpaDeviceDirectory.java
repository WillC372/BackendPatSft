package com.aquasense.device;

import com.aquasense.measurement.DeviceDirectory;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JpaDeviceDirectory implements DeviceDirectory {

    private final DeviceRepository repository;

    public JpaDeviceDirectory(DeviceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<Long, String> namesFor(Long userId, Set<Long> deviceIds) {
        return repository.findByOwnerIdAndIdIn(userId, deviceIds).stream()
            .collect(Collectors.toMap(Device::getId, Device::getName));
    }
}
