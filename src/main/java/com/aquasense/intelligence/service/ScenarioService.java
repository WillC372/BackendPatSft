package com.aquasense.intelligence.service;

import com.aquasense.measurement.domain.Measurement;
import com.aquasense.measurement.service.MeasurementService;
import com.aquasense.shared.DomainException;
import com.aquasense.waterprofile.domain.PhClassification;
import com.aquasense.waterprofile.domain.WaterProfile;
import com.aquasense.waterprofile.service.WaterProfileService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {

    public record Scenario(boolean hypothetical, BigDecimal baselinePh, PhClassification baseline,
                           BigDecimal scenarioPh, PhClassification scenario, String comparison,
                           String followUp, String notice) {
    }

    private final WaterProfileService profiles;
    private final MeasurementService measurements;

    public ScenarioService(WaterProfileService profiles, MeasurementService measurements) {
        this.profiles = profiles;
        this.measurements = measurements;
    }

    public Scenario simulate(UUID ownerId, UUID profileId, BigDecimal explicitPh, BigDecimal delta) {
        WaterProfile profile = profiles.get(profileId, ownerId);
        BigDecimal baselinePh = measurements.latest(ownerId).map(Measurement::getPh).orElse(null);
        BigDecimal scenarioPh;
        if (explicitPh != null) {
            scenarioPh = explicitPh;
        } else if (delta != null && baselinePh != null) {
            scenarioPh = baselinePh.add(delta);
        } else {
            throw new DomainException(HttpStatus.BAD_REQUEST, "SCENARIO_INCOMPLETE",
                    "Indique un valor de pH o un cambio (delta) y tenga al menos una medición registrada.");
        }
        scenarioPh = scenarioPh.setScale(2, RoundingMode.HALF_UP);
        PhClassification scenario = profile.classify(scenarioPh);
        PhClassification baseline = baselinePh == null ? null : profile.classify(baselinePh);
        String comparison = baseline == null
                ? "No hay una medición previa con la cual comparar."
                : "Pasaría de «" + label(baseline) + "» (pH " + baselinePh.toPlainString() + ") a «" + label(scenario)
                        + "» (pH " + scenarioPh.toPlainString() + ")."
                        + (baseline.status() == scenario.status() ? " La zona del perfil no cambia." : "");
        String followUp = switch (scenario.status()) {
            case OPTIMAL -> "Un valor así estaría dentro del rango recomendado del perfil «" + profile.getName()
                    + "»; mantenga el monitoreo.";
            case CAUTION -> "Sería una zona de precaución: convendría repetir la medición, revisar la calibración del "
                    + "sensor y observar si el valor se mantiene.";
            case CRITICAL -> "Sería un valor muy alejado del rango del perfil: se requeriría verificación con un "
                    + "método independiente y revisar la causa antes de actuar.";
        };
        return new Scenario(true, baselinePh, baseline, scenarioPh, scenario, comparison, followUp,
                "Escenario hipotético: no es una predicción de lo que ocurrirá ni demuestra que el agua sea segura o "
                        + "potable.");
    }

    private static String label(PhClassification c) {
        String zone = switch (c.status()) {
            case OPTIMAL -> "en rango recomendado";
            case CAUTION -> "precaución";
            case CRITICAL -> "crítico";
        };
        String acidity = switch (c.acidity()) {
            case ACIDIC -> "ácida";
            case NEUTRAL -> "neutra";
            case ALKALINE -> "alcalina";
        };
        return acidity + ", " + zone;
    }
}
