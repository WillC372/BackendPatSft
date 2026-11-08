package com.aquasense.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.measurement.validation.MeasurementValidationChain;
import com.aquasense.measurement.validation.ReadingContext;
import com.aquasense.measurement.validation.ValidationReport;
import com.aquasense.measurement.validation.ValidationStatus;
import com.aquasense.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValidationChainTest {

    private final MeasurementValidationChain chain = new MeasurementValidationChain();

    @Test
    void validReadingPassesWholeChain() {
        ValidationReport report = chain.validate(new ReadingContext(7.3, 24.0, Instant.now(), MeasurementSource.MANUAL, Optional.empty()));
        assertThat(report.status()).isEqualTo(ValidationStatus.VALID);
    }

    @Test
    void outOfPhysicalRangeIsRejectedAndStopsChain() {
        ValidationReport report = chain.validate(new ReadingContext(15.0, 80.0, Instant.now(), MeasurementSource.SIMULATED, Optional.empty()));
        assertThat(report.status()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void futureTimestampIsRejected() {
        ValidationReport report = chain.validate(new ReadingContext(7.0, null, Instant.now().plus(Duration.ofHours(1)),
            MeasurementSource.MANUAL, Optional.empty()));
        assertThat(report.isRejected()).isTrue();
    }

    @Test
    void suddenChangeAndSimulatedSourceProduceWarnings() {
        Measurement previous = TestData.measurement(1, 7.0, Instant.now().minus(Duration.ofMinutes(5)), TestData.pool());
        ValidationReport report = chain.validate(new ReadingContext(8.5, null, Instant.now(), MeasurementSource.SIMULATED,
            Optional.of(previous)));
        assertThat(report.status()).isEqualTo(ValidationStatus.WARNING);
        assertThat(report.warnings()).hasSize(2);
        assertThat(report.notes()).contains("Cambio brusco").contains("simulador");
    }
}
