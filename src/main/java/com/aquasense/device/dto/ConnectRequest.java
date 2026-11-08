package com.aquasense.device.dto;

import com.aquasense.device.hardware.MeterProtocol;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConnectRequest(
    @NotNull(message = "Elige el tipo de medidor.") MeterProtocol protocol,
    @Size(max = 160) String port,
    @Size(max = 80, message = "Máximo 80 caracteres.") String name) {
}
