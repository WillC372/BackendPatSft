package com.aquasense.device.api;

import com.aquasense.device.domain.ConnectionType;
import com.aquasense.device.domain.Device;
import com.aquasense.device.domain.SourceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record DeviceRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 80) String manufacturer,
            @Size(max = 80) String model,
            @NotBlank @Size(max = 120) String externalId,
            @NotNull ConnectionType connectionType) {
    }

    public record DeviceResponse(UUID id, String name, String manufacturer, String model, String externalId,
                                 ConnectionType connectionType, SourceKind sourceKind, Instant lastSeenAt) {

        public static DeviceResponse from(Device d) {
            return new DeviceResponse(d.getId(), d.getName(), d.getManufacturer(), d.getModel(), d.getExternalId(),
                    d.getConnectionType(), d.getSourceKind(), d.getLastSeenAt());
        }
    }
}
