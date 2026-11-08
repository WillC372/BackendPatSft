package com.aquasense.intelligence.analysis;

import com.aquasense.measurement.Measurement;
import com.aquasense.profile.WaterProfile;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class SuddenChangeRule implements AnomalyRule {

    static final double CAUTION_DELTA = 0.5;
    static final double CRITICAL_DELTA = 1.0;
    private static final Duration WINDOW = Duration.ofHours(1);

    @Override
    public Optional<Anomaly> evaluate(Measurement current, List<Measurement> previous, WaterProfile profile) {
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        Measurement last = previous.get(previous.size() - 1);
        Duration elapsed = Duration.between(last.getMeasuredAt(), current.getMeasuredAt());
        double delta = current.getPh() - last.getPh();
        if (elapsed.isNegative() || elapsed.compareTo(WINDOW) > 0 || Math.abs(delta) < CAUTION_DELTA) {
            return Optional.empty();
        }
        boolean critical = Math.abs(delta) >= CRITICAL_DELTA;
        String message = String.format(Locale.US, "El pH %s %.2f unidades en %d minutos (%.2f -> %.2f).",
            delta > 0 ? "subió" : "bajó", Math.abs(delta), Math.max(1, elapsed.toMinutes()), last.getPh(), current.getPh());
        return Optional.of(new Anomaly("SUDDEN_CHANGE", critical ? Anomaly.Severity.CRITICAL : Anomaly.Severity.CAUTION,
            "Cambio brusco de pH", message, current.getId(), current.getMeasuredAt(), current.getPh()));
    }
}
