package com.aquasense.device.dto;

import jakarta.validation.constraints.Size;

public record SaveReadingRequest(@Size(max = 300, message = "Máximo 300 caracteres.") String note) {
}
