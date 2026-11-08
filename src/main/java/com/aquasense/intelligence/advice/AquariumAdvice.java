package com.aquasense.intelligence.advice;

import com.aquasense.profile.ProfileType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AquariumAdvice extends BaseAdviceStrategy {

    @Override
    public ProfileType type() {
        return ProfileType.AQUARIUM;
    }

    @Override
    protected String expertNote() {
        return "Si los peces muestran estrés (respiración rápida, letargo), consulta a un especialista en acuarios.";
    }

    @Override
    protected List<String> lowCauses() {
        return List.of("Acumulación de desechos y ácidos orgánicos.", "Exceso de CO2 (plantas, inyección de CO2).",
            "Pocos cambios de agua.");
    }

    @Override
    protected List<String> highCauses() {
        return List.of("Sustrato o rocas calcáreas.", "Agua de reposición alcalina.");
    }

    @Override
    protected List<String> lowActions() {
        return List.of("Realiza un cambio parcial de agua del 10-20 %.",
            "Corrige de forma gradual: no más de 0.2-0.3 unidades de pH por día.",
            "Revisa el filtro y retira restos de comida.");
    }

    @Override
    protected List<String> highActions() {
        return List.of("Revisa rocas y decoraciones; retira las calcáreas si no son adecuadas para tus especies.",
            "Haz cambios parciales con agua de pH más bajo, de forma gradual.");
    }
}
