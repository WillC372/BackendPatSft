package com.aquasense.intelligence;

import com.aquasense.intelligence.advice.Advice;
import com.aquasense.intelligence.advice.AdviceStrategyResolver;
import com.aquasense.profile.Classification;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.WaterProfile;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * "What if" scenario simulator: classifies a hypothetical pH with the same rules
 * as a real measurement and compares it with the current value. It saves nothing in the history.
 */
@Component
public class ScenarioSimulator {

    private final PhClassifier classifier;
    private final AdviceStrategyResolver resolver;

    public ScenarioSimulator(PhClassifier classifier, AdviceStrategyResolver resolver) {
        this.classifier = classifier;
        this.resolver = resolver;
    }

    public Result simulate(Double currentPh, double hypotheticalPh, WaterProfile profile) {
        Classification hypothetical = classifier.classify(hypotheticalPh, profile);
        Classification current = currentPh == null ? null : classifier.classify(currentPh, profile);
        Advice advice = resolver.forType(profile.getType()).advise(hypothetical, profile);
        String comparison;
        if (current == null) {
            comparison = "No hay una medición actual para comparar.";
        } else {
            double delta = hypotheticalPh - currentPh;
            comparison = String.format(Locale.US, "Pasar de %.2f a %.2f (%+.2f) cambia el estado de \"%s\" a \"%s\".",
                currentPh, hypotheticalPh, delta, current.status().label(), hypothetical.status().label());
            if (Math.abs(delta) >= 1) {
                comparison += " Un cambio de 1 unidad de pH equivale a multiplicar por 10 la concentración de iones H+.";
            }
        }
        return new Result(current, hypothetical, comparison, advice);
    }

    public record Result(Classification current, Classification hypothetical, String comparison, Advice advice) {
    }
}
