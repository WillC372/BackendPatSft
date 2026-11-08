package com.aquasense.intelligence.advice;

import com.aquasense.profile.ProfileType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DrinkingWaterAdvice extends BaseAdviceStrategy {

    @Override
    public ProfileType type() {
        return ProfileType.DRINKING;
    }

    @Override
    protected String optimalAction() {
        return "El pH es adecuado, pero no demuestra que el agua sea potable: la potabilidad requiere análisis "
            + "microbiológicos y químicos de laboratorio.";
    }

    @Override
    protected boolean expertAlways() {
        return true;
    }

    @Override
    protected String expertNote() {
        return "Para agua de consumo, un pH fuera de rango justifica un análisis de laboratorio. "
            + "El pH por sí solo no confirma ni descarta contaminación.";
    }

    @Override
    protected List<String> lowCauses() {
        return List.of("Agua blanda o con CO2 disuelto.", "Corrosión de tuberías metálicas.",
            "Infiltración de aguas ácidas en la fuente.");
    }

    @Override
    protected List<String> highCauses() {
        return List.of("Exceso de carbonatos o bicarbonatos.", "Tratamiento con cal o soda mal ajustado.");
    }

    @Override
    protected List<String> lowActions() {
        return List.of("Evita dejar el agua en reposo en tuberías metálicas antes de consumirla.",
            "Consulta al proveedor del servicio o a un laboratorio; un pH bajo puede disolver metales de las tuberías.",
            "Un filtro neutralizador (calcita) puede corregirlo, pero debe instalarlo un técnico.");
    }

    @Override
    protected List<String> highActions() {
        return List.of("Revisa incrustaciones en grifos y calentadores.",
            "Consulta al proveedor del servicio sobre el tratamiento aplicado.");
    }
}
