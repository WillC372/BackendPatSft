package com.aquasense.intelligence.advice;

import com.aquasense.profile.ProfileType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IrrigationAdvice extends BaseAdviceStrategy {

    @Override
    public ProfileType type() {
        return ProfileType.IRRIGATION;
    }

    @Override
    protected String expertNote() {
        return "Para correcciones de pH en riego, consulta a un agrónomo: la dosis depende del cultivo y del suelo.";
    }

    @Override
    protected List<String> lowCauses() {
        return List.of("Fuente de agua ácida.", "Fertilizantes acidificantes (amoniacales).");
    }

    @Override
    protected List<String> highCauses() {
        return List.of("Agua de pozo con bicarbonatos.", "Fuente con alto contenido de carbonatos.");
    }

    @Override
    protected List<String> lowActions() {
        return List.of("Un pH bajo puede liberar aluminio y manganeso y reducir calcio y magnesio.",
            "Evalúa la corrección con bases agrícolas según recomendación técnica.");
    }

    @Override
    protected List<String> highActions() {
        return List.of("Un pH alto reduce la disponibilidad de hierro, fósforo y zinc.",
            "Revisa obstrucciones por carbonatos en goteros.",
            "Evalúa acidificar el agua de riego con la dosis que indique un técnico.");
    }
}
