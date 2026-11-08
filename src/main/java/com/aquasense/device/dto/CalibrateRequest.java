package com.aquasense.device.dto;

import com.aquasense.device.hardware.CalibrationPoint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CalibrateRequest(
    @NotNull(message = "Elige el punto de calibración.") CalibrationPoint point,
    @DecimalMin("1.0") @DecimalMax("13.0") Double bufferValue) {
}
