package com.aquasense.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.measurement.validation.MeasurementValidationChain;
import com.aquasense.measurement.validation.ValidationContext;
import com.aquasense.measurement.validation.ValidationContext.PreviousReading;
import com.aquasense.measurement.validation.ValidationOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MeasurementValidationChainTest {

    private final MeasurementValidationChain chain = new MeasurementValidationChain();
    private final Instant now = Instant.parse("2026-11-02T12:00:00Z");

    private ValidationOutcome run(String ph, Instant measuredAt, PreviousReading previous) {
        return chain.validate(new ValidationContext(new BigDecimal(ph), measuredAt, now, Optional.ofNullable(previous)));
    }

    @Test
    void acceptsNormalReading() {
        ValidationOutcome outcome = run("7.12", now.minusSeconds(30), null);
        assertThat(outcome.isRejected()).isFalse();
        assertThat(outcome.isSuspect()).isFalse();
        assertThat(outcome.getNotes()).isEmpty();
    }

    @Test
    void rejectsValuesOutsideScale() {
        assertThat(run("14.5", now, null).isRejected()).isTrue();
        assertThat(run("-0.5", now, null).isRejected()).isTrue();
        assertThat(run("0", now, null).isRejected()).isFalse();
        assertThat(run("14", now, null).isRejected()).isFalse();
    }

    @Test
    void rejectsFutureTimestampsBeyondClockTolerance() {
        assertThat(run("7", now.plusSeconds(4 * 60), null).isRejected()).isFalse();
        assertThat(run("7", now.plusSeconds(6 * 60), null).isRejected()).isTrue();
    }

    @Test
    void flagsStaleReadingsAsSuspectNotRejected() {
        ValidationOutcome outcome = run("7", now.minusSeconds(25 * 3600), null);
        assertThat(outcome.isRejected()).isFalse();
        assertThat(outcome.isSuspect()).isTrue();
    }

    @Test
    void flagsAbruptChangeOnlyWhenCloseInTime() {
        PreviousReading prev = new PreviousReading(new BigDecimal("7.10"), now.minusSeconds(120));
        assertThat(run("4.50", now, prev).isSuspect()).isTrue();
        assertThat(run("8.50", now, prev).isSuspect()).isFalse();
        PreviousReading old = new PreviousReading(new BigDecimal("7.10"), now.minusSeconds(3600));
        assertThat(run("4.50", now, old).isSuspect()).isFalse();
    }

    @Test
    void rejectionStopsTheChainSoLaterHandlersDoNotAddNotes() {
        PreviousReading prev = new PreviousReading(new BigDecimal("7.10"), now.minusSeconds(60));
        ValidationOutcome outcome = run("15", now, prev);
        assertThat(outcome.isRejected()).isTrue();
        assertThat(outcome.getNotes()).hasSize(1);
    }
}
