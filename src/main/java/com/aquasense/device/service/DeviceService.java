package com.aquasense.device.service;

import com.aquasense.device.domain.ConnectionType;
import com.aquasense.device.domain.Device;
import com.aquasense.billing.service.LimitGuard;
import com.aquasense.device.repository.DeviceRepository;
import com.aquasense.shared.DomainException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceRepository devices;
    private final LimitGuard limits;

    public DeviceService(DeviceRepository devices, LimitGuard limits) {
        this.devices = devices;
        this.limits = limits;
    }

    @Transactional
    public Device register(UUID ownerId, String name, String manufacturer, String model, String externalId,
                           ConnectionType type) {
        if (devices.existsByOwnerIdAndExternalId(ownerId, externalId.trim())) {
            throw new DomainException(HttpStatus.CONFLICT, "DEVICE_ALREADY_REGISTERED",
                    "Este dispositivo ya está vinculado a su cuenta.");
        }
        limits.requireDeviceSlot(ownerId);
        try {
            return devices.saveAndFlush(new Device(ownerId, name.trim(), blankToNull(manufacturer),
                    blankToNull(model), externalId.trim(), type));
        } catch (DataIntegrityViolationException e) {
            throw new DomainException(HttpStatus.CONFLICT, "DEVICE_ALREADY_REGISTERED",
                    "Este dispositivo ya está vinculado a su cuenta.");
        }
    }

    @Transactional(readOnly = true)
    public List<Device> list(UUID ownerId) {
        return devices.findByOwnerIdOrderByNameAsc(ownerId);
    }

    @Transactional(readOnly = true)
    public Device get(UUID id, UUID ownerId) {
        return devices.findByIdAndOwnerId(id, ownerId).orElseThrow(() ->
                new DomainException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "El dispositivo no existe."));
    }

    @Transactional
    public void unlink(UUID id, UUID ownerId) {
        Device device = get(id, ownerId);
        try {
            devices.delete(device);
            devices.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DomainException(HttpStatus.CONFLICT, "DEVICE_HAS_MEASUREMENTS",
                    "No se puede desvincular un dispositivo con mediciones registradas.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
