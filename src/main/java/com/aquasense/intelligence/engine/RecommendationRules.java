package com.aquasense.intelligence.engine;

import com.aquasense.waterprofile.domain.WaterType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecommendationRules {

    private RecommendationRules() {
    }

    public static List<String> build(AnalysisInput in, Statistics stats, List<AnomalyFinding> findings,
                                     TrendResult trend, boolean enoughData) {
        List<String> out = new ArrayList<>();
        if (!enoughData) {
            out.add("Registre más mediciones (idealmente varias por día durante varios días) para obtener un "
                    + "análisis confiable.");
            return out;
        }
        double latest = in.samples().get(in.samples().size() - 1).ph();
        if (latest < in.optimalMin() || latest > in.optimalMax()) {
            boolean low = latest < in.optimalMin();
            out.add(String.format(Locale.US, "La última lectura (%.2f) está %s del rango recomendado (%.2f–%.2f) del "
                    + "perfil «%s». Repita la medición, verifique que el sensor esté calibrado y revise la fuente de "
                    + "agua.", latest, low ? "por debajo" : "por encima", in.optimalMin(), in.optimalMax(),
                    in.profileName()));
            out.add(contextAdvice(in.waterType(), low));
        } else {
            out.add(String.format(Locale.US, "La última lectura (%.2f) está dentro del rango recomendado del perfil "
                    + "«%s». Mantenga su rutina de monitoreo.", latest, in.profileName()));
        }
        if (trend.direction() == TrendDirection.RISING || trend.direction() == TrendDirection.FALLING) {
            out.add("Se observa una tendencia sostenida: conviene investigar qué cambió (fuente de agua, "
                    + "tratamientos, vegetación, uso) antes de que la lectura salga del rango.");
        }
        if (findings.stream().anyMatch(f -> f.severity() == Severity.CRITICAL)) {
            out.add("Hay lecturas críticas. Confírmelas con una segunda medición o un método independiente (por "
                    + "ejemplo, un kit de prueba) antes de actuar.");
        }
        if (findings.stream().anyMatch(f -> f.type() == FindingType.ABRUPT_CHANGE)) {
            out.add("Los cambios bruscos pueden indicar un error del sensor: revise la sonda y calibre si el "
                    + "medidor lo permite.");
        }
        if (stats.standardDeviation() > 0.5) {
            out.add("La variabilidad de las mediciones es alta; revise la técnica de medición y el estado del "
                    + "electrodo.");
        }
        if (in.waterType() == WaterType.DRINKING) {
            out.add("Para decidir si el agua es apta para consumo se requieren análisis microbiológicos y "
                    + "fisicoquímicos de un laboratorio; el pH solo no basta.");
        }
        return out;
    }

    private static String contextAdvice(WaterType type, boolean low) {
        return switch (type) {
            case POOL -> "En piscinas, un pH fuera de rango puede reducir la eficacia de la desinfección e irritar a "
                    + "los bañistas. Siga las instrucciones del fabricante de los productos de ajuste y verifique "
                    + "también cloro libre y alcalinidad.";
            case AQUARIUM -> "En acuarios los cambios de pH deben ser graduales: un ajuste rápido puede estresar a los "
                    + "peces. Consulte los requisitos de sus especies y revise el KH y el GH.";
            case AGRICULTURE -> "En riego, un pH " + (low ? "bajo" : "alto") + " puede afectar la disponibilidad de "
                    + "nutrientes y corroer o incrustar el sistema. Considere también la conductividad eléctrica.";
            case DRINKING -> "Un pH fuera de 6,5–8,5 puede asociarse a corrosión de tuberías o a sabor alterado; "
                    + "consulte a la entidad o laboratorio competente.";
            default -> "Identifique la causa probable y vuelva a medir pasado un tiempo prudente.";
        };
    }
}
