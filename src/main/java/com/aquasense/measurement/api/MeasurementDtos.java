package com.aquasense.measurement.api;

import com.aquasense.device.domain.SourceKind;
import com.aquasense.measurement.domain.Measurement;
import com.aquasense.measurement.domain.ValidationStatus;
import com.aquasense.waterprofile.domain.AcidityClass;
import com.aquasense.waterprofile.domain.RangeStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class MeasurementDtos {

    public static final String DISCLAIMER = "El pH es solo un indicador: por sí solo no demuestra que el agua sea "
            + "potable, segura ni esté libre de contaminantes.";

    private MeasurementDtos() {
    }

    public record MeasurementRequest(
            @NotNull UUID deviceId,
            @NotNull UUID profileId,
            @NotNull @DecimalMin(value = "-100") @DecimalMax(value = "100") BigDecimal ph,
            Instant measuredAt) {
    }

    public record MeasurementResponse(UUID id, UUID deviceId, UUID profileId, BigDecimal ph, Instant measuredAt,
                                      SourceKind source, AcidityClass acidity, RangeStatus rangeStatus,
                                      ValidationStatus validationStatus, String validationNotes, String disclaimer) {

        public static MeasurementResponse from(Measurement m) {
            return new MeasurementResponse(m.getId(), m.getDeviceId(), m.getProfileId(), m.getPh(), m.getMeasuredAt(),
                    m.getSourceKind(), m.getAcidity(), m.getRangeStatus(), m.getValidationStatus(),
                    m.getValidationNotes(), DISCLAIMER);
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

        public static <E, T> PageResponse<T> from(Page<E> page, java.util.function.Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
