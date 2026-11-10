package com.aquasense.device.dto;

import com.aquasense.device.hardware.MeterProtocol;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Reading of a meter connected to the user's own computer and read by the browser (Web Serial).
 * {@code deviceName} identifies the meter in the user's device list.
 */
public record BrowserReadingRequest(
    @NotNull(message = "Falta el valor de pH.") @DecimalMin(value = "0.0", message = "El pH debe estar entre 0 y 14.")
    @DecimalMax(value = "14.0", message = "El pH debe estar entre 0 y 14.") Double ph,
    @DecimalMin(value = "-10.0", message = "Temperatura fuera de rango.") @DecimalMax(value = "110.0", message = "Temperatura fuera de rango.")
    Double temperatureC,
    @NotNull(message = "Indica el protocolo del medidor.") MeterProtocol protocol,
    @NotBlank(message = "Indica el nombre del medidor.") @Size(max = 80, message = "Nombre de medidor demasiado largo.") String deviceName,
    @Size(max = 200, message = "La nota admite hasta 200 caracteres.") String note) {
}
