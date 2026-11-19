package com.aquaassistant.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquaassistant.alert.analysis.Anomaly;
import com.aquaassistant.alert.analysis.PhAlterationRule;
import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.profile.PhRange;
import com.aquaassistant.profile.ProfileType;
import com.aquaassistant.profile.WaterProfile;
import com.aquaassistant.support.TestData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** An alteration of the pH: detected against the last hour, and dangerous only when the profile says critical. */
class PhAlterationRuleTest {

    private final PhAlterationRule rule = new PhAlterationRule();
    private final WaterProfile pool = TestData.pool();
    private final Instant now = Instant.parse("2026-11-19T15:00:00Z");

    private Measurement at(long id, double ph, long secondsAgo, WaterProfile profile) {
        return TestData.measurement(id, ph, now.minusSeconds(secondsAgo), profile);
    }

    @Test
    void somethingThrownIntoThePoolIsDangerousAndSaysToGetOut() {
        List<Measurement> before = List.of(at(1, 7.5, 600, pool), at(2, 7.5, 300, pool));
        Anomaly a = rule.evaluate(at(3, 5.2, 0, pool), before, pool).orElseThrow();
        assertThat(a.type()).isEqualTo(PhAlterationRule.TYPE);
        assertThat(a.title()).isEqualTo("Alteración del pH en el agua");
        assertThat(a.severity()).isEqualTo(Anomaly.Severity.CRITICAL);
        assertThat(a.message()).startsWith("El pH bajó de 7.50 a 5.20 en 10 minutos.")
            .contains("Es peligroso").contains("Piscina").contains("salgan del agua");
    }

    @Test
    void aMeterEverySecondsSeesAChangeThatBuildsUpOverMinutes() {
        // Every 10 s the pH drops 0.05: never 0.5 between two readings, but 0.6 in two minutes.
        List<Measurement> before = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            before.add(at(i + 1, 7.6 - i * 0.05, 120 - i * 10L, pool));
        }
        Anomaly a = rule.evaluate(at(20, 7.0, 0, pool), before, pool).orElseThrow();
        assertThat(a.message()).startsWith("El pH bajó de 7.60 a 7.00 en 2 minutos.").contains("no es peligroso");
        assertThat(a.severity()).isEqualTo(Anomaly.Severity.CAUTION);
    }

    @Test
    void aChangeInsideTheIdealRangeIsNotDangerousButAsksToMeasureAgain() {
        WaterProfile wide = new WaterProfile(null, "Uso general", ProfileType.GENERAL, new PhRange(6.5, 8.5, 6.0, 9.0), "", "");
        Anomaly a = rule.evaluate(at(2, 8.0, 0, wide), List.of(at(1, 7.0, 240, wide)), wide).orElseThrow();
        assertThat(a.message()).contains("subió de 7.00 a 8.00").contains("sigue en el rango ideal").contains("Vuelve a medir");
        assertThat(a.severity()).isEqualTo(Anomaly.Severity.CAUTION);
    }

    @Test
    void eachKindOfWaterGetsItsAdvice() {
        WaterProfile aquarium = new WaterProfile(null, "Acuario", ProfileType.AQUARIUM, new PhRange(6.5, 7.5, 6.0, 8.0), "", "");
        WaterProfile drinking = new WaterProfile(null, "Agua potable", ProfileType.DRINKING, new PhRange(6.5, 8.5, 6.0, 9.0), "", "");
        assertThat(rule.evaluate(at(2, 4.5, 0, aquarium), List.of(at(1, 7.0, 60, aquarium)), aquarium).orElseThrow().message())
            .contains("Los peces corren peligro");
        assertThat(rule.evaluate(at(2, 10.5, 0, drinking), List.of(at(1, 7.5, 60, drinking)), drinking).orElseThrow().message())
            .contains("No tomes esta agua");
    }

    @Test
    void goingIntoTheCriticalLevelIsAnAlterationEvenIfSlow() {
        Anomaly a = rule.evaluate(at(2, 8.1, 0, pool), List.of(at(1, 7.9, 120, pool)), pool).orElseThrow();
        assertThat(a.severity()).isEqualTo(Anomaly.Severity.CRITICAL);
        assertThat(a.message()).startsWith("El pH subió de 7.90 a 8.10");
    }

    @Test
    void smallChangesOldReadingsAndStayingCriticalAreNotAnAlteration() {
        assertThat(rule.evaluate(at(2, 7.6, 0, pool), List.of(at(1, 7.4, 60, pool)), pool)).isEmpty();
        assertThat(rule.evaluate(at(2, 5.0, 0, pool), List.of(at(1, 7.5, 2 * 3600, pool)), pool)).isEmpty();
        assertThat(rule.evaluate(at(2, 6.5, 0, pool), List.of(at(1, 6.6, 60, pool)), pool)).isEmpty();
        assertThat(rule.evaluate(at(1, 5.0, 0, pool), List.of(), pool)).isEmpty();
    }
}
