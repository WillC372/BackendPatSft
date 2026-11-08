package com.aquasense.profile;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Classifies a pH value against the ranges of the active profile.
 * Ideal: inside [idealMin, idealMax]. Caution: outside the ideal range but inside the caution band.
 * Critical: outside the caution band.
 */
@Component
public class PhClassifier {

    public Classification classify(double ph, WaterProfile profile) {
        PhNature nature = PhNature.of(ph);
        PhRange range = profile.ranges();
        PhStatus status;
        if (ph >= range.idealMin() && ph <= range.idealMax()) {
            status = PhStatus.OPTIMAL;
        } else if (ph >= range.cautionMin() && ph <= range.cautionMax()) {
            status = PhStatus.CAUTION;
        } else {
            status = PhStatus.CRITICAL;
        }
        return new Classification(ph, nature, status, status == PhStatus.OPTIMAL, explain(ph, nature, status, profile));
    }

    public String explain(double ph, PhNature nature, PhStatus status, WaterProfile profile) {
        String value = String.format(Locale.US, "%.2f", ph);
        String base = "pH " + value + ": agua " + nature.label().toLowerCase(Locale.ROOT) + ". ";
        String range = "Rango recomendado para " + profile.getName() + ": " + profile.rangeLabel() + ".";
        return switch (status) {
            case OPTIMAL -> base + "Dentro del rango recomendado. " + range;
            case CAUTION -> base + (ph < profile.getIdealMin() ? "Ligeramente por debajo" : "Ligeramente por encima")
                + " del rango recomendado. Conviene vigilar la tendencia. " + range;
            case CRITICAL -> base + (ph < profile.getIdealMin() ? "Muy por debajo" : "Muy por encima")
                + " del rango recomendado. Requiere acción. " + range;
        };
    }
}
