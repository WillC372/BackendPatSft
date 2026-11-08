package com.aquasense.intelligence.analysis;

import com.aquasense.measurement.Measurement;
import com.aquasense.profile.PhStatus;
import com.aquasense.profile.WaterProfile;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class OutOfRangeRule implements AnomalyRule {

    @Override
    public Optional<Anomaly> evaluate(Measurement current, List<Measurement> previous, WaterProfile profile) {
        if (current.getStatus() == PhStatus.OPTIMAL) {
            return Optional.empty();
        }
        boolean critical = current.getStatus() == PhStatus.CRITICAL;
        String direction = current.getPh() < profile.getIdealMin() ? "por debajo" : "por encima";
        String message = String.format(Locale.US, "pH %.2f %s del rango recomendado para %s (%s).", current.getPh(),
            direction, profile.getName(), profile.rangeLabel());
        return Optional.of(new Anomaly("OUT_OF_RANGE", critical ? Anomaly.Severity.CRITICAL : Anomaly.Severity.CAUTION,
            critical ? "pH en nivel crítico" : "pH fuera del rango ideal", message, current.getId(),
            current.getMeasuredAt(), current.getPh()));
    }
}
