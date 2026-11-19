package com.aquaassistant.measurement;

import com.aquaassistant.measurement.dto.MeasurementResponse;
import com.aquaassistant.measurement.dto.NewReading;
import com.aquaassistant.security.InternalKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Measurements the AI service read from a photo and the person confirmed. They are recorded here like any other one
 * (validated, classified, in the active place) so the alerts and notices fire. Only the AI service calls it, with
 * the internal key both share.
 */
@RestController
public class InternalMeasurementController {

    private final MeasurementService measurements;
    private final InternalKey internalKey;

    public InternalMeasurementController(MeasurementService measurements, InternalKey internalKey) {
        this.measurements = measurements;
        this.internalKey = internalKey;
    }

    public record PhotoReading(
        @NotNull Long userId,
        @NotNull @DecimalMin("0.0") @DecimalMax("14.0") Double ph,
        Double temperatureC,
        @Size(max = 300) String note) {
    }

    @PostMapping("/api/v1/internal/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse photoReading(@Valid @RequestBody PhotoReading reading,
                                            @RequestHeader(value = InternalKey.HEADER, required = false) String key) {
        internalKey.check(key);
        return measurements.toResponse(reading.userId(), measurements.record(reading.userId(),
            new NewReading(reading.ph(), reading.temperatureC(), Instant.now(), MeasurementSource.PHOTO, null, reading.note())));
    }
}
