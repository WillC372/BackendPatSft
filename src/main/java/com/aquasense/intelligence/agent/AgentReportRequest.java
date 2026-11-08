package com.aquasense.intelligence.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AgentReportRequest(
    @Min(value = 1, message = "Mínimo 1 día.") @Max(value = 365, message = "Máximo 365 días.") Integer days,
    @Size(max = 300, message = "Máximo 300 caracteres.") String focus) {
}
