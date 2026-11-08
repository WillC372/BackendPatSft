package com.aquasense.intelligence.advice;

import com.aquasense.profile.Classification;
import com.aquasense.profile.PhStatus;
import com.aquasense.profile.WaterProfile;
import java.util.List;

/**
 * Template Method: defines the common flow (optimal, low or high) and leaves to each profile
 * its specific causes and actions.
 */
public abstract class BaseAdviceStrategy implements AdviceStrategy {

    @Override
    public final Advice advise(Classification c, WaterProfile profile) {
        if (c.status() == PhStatus.OPTIMAL) {
            return new Advice("El agua está dentro del rango recomendado para " + profile.getName()
                + ". Continúa con el monitoreo regular.", List.of(), List.of(optimalAction()), false, null);
        }
        boolean low = c.ph() < profile.getIdealMin();
        boolean critical = c.status() == PhStatus.CRITICAL;
        String summary = (low ? "El pH está por debajo" : "El pH está por encima") + " del rango recomendado ("
            + profile.rangeLabel() + ")" + (critical ? " en nivel crítico." : ".");
        return new Advice(summary, low ? lowCauses() : highCauses(), low ? lowActions() : highActions(),
            critical || expertAlways(), critical || expertAlways() ? expertNote() : null);
    }

    protected String optimalAction() {
        return "Mantener la frecuencia de medición y registrar cualquier cambio en el uso del agua.";
    }

    protected boolean expertAlways() {
        return false;
    }

    protected String expertNote() {
        return "Confirma con una segunda medición y, si persiste, consulta a un especialista.";
    }

    protected abstract List<String> lowCauses();

    protected abstract List<String> highCauses();

    protected abstract List<String> lowActions();

    protected abstract List<String> highActions();
}
