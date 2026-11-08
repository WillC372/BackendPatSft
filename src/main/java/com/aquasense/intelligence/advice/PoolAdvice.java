package com.aquasense.intelligence.advice;

import com.aquasense.profile.ProfileType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PoolAdvice extends BaseAdviceStrategy {

    @Override
    public ProfileType type() {
        return ProfileType.POOL;
    }

    @Override
    protected List<String> lowCauses() {
        return List.of("Lluvia intensa.", "Uso de cloro ácido (tricloro) o exceso de reductor de pH.",
            "Alcalinidad total baja.");
    }

    @Override
    protected List<String> highCauses() {
        return List.of("Uso de hipoclorito de sodio o calcio.", "Aireación intensa (cascadas, jacuzzi).",
            "Agua de reposición muy alcalina.");
    }

    @Override
    protected List<String> lowActions() {
        return List.of("Mide la alcalinidad total (referencia 80-120 ppm) antes de corregir.",
            "Agrega incrementador de pH (carbonato de sodio) en dosis pequeñas según el fabricante.",
            "Recircula el agua 4-6 horas y vuelve a medir antes de una segunda dosis.");
    }

    @Override
    protected List<String> highActions() {
        return List.of("Agrega reductor de pH (bisulfato de sodio) en dosis pequeñas según el fabricante.",
            "Con pH alto el cloro pierde eficacia: verifica el cloro libre.",
            "Recircula el agua y vuelve a medir después de 4-6 horas.");
    }
}
