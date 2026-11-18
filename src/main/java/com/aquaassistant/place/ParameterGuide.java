package com.aquaassistant.place;

import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.measurement.WaterParams;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Orientative ranges of the other water parameters for each kind of place, and the latest value of each one.
 * They are general guidance for care, not a standard: the app says so next to them.
 */
public final class ParameterGuide {

    private ParameterGuide() {
    }

    /** Range (low, high) or null when there is no general guidance for that kind of water. */
    public record Range(double low, double high) {
    }

    public record Reading(String key, String label, String unit, Double value, Instant measuredAt, Range range,
                          String status, String advice) {
    }

    private record Param(String key, String label, Function<WaterParams, Double> value) {
    }

    private static final List<Param> PARAMS = List.of(
        new Param("freeChlorine", "Cloro libre", WaterParams::freeChlorine),
        new Param("totalAlkalinity", "Alcalinidad total", WaterParams::totalAlkalinity),
        new Param("calciumHardness", "Dureza cálcica", WaterParams::calciumHardness),
        new Param("cyanuricAcid", "Ácido cianúrico", WaterParams::cyanuricAcid));

    public static Range rangeFor(PlaceKind kind, String key) {
        return switch (kind) {
            case POOL -> switch (key) {
                case "freeChlorine" -> new Range(1, 3);
                case "totalAlkalinity" -> new Range(60, 120);
                case "calciumHardness" -> new Range(200, 400);
                case "cyanuricAcid" -> new Range(30, 50);
                default -> null;
            };
            case SPA -> switch (key) {
                case "freeChlorine" -> new Range(3, 5);
                case "totalAlkalinity" -> new Range(60, 120);
                case "calciumHardness" -> new Range(150, 250);
                case "cyanuricAcid" -> new Range(30, 50);
                default -> null;
            };
            case DRINKING -> "freeChlorine".equals(key) ? new Range(0.2, 4) : null;
            case AQUARIUM, POND -> switch (key) {
                case "freeChlorine" -> new Range(0, 0);
                case "totalAlkalinity" -> new Range(50, 140);
                default -> null;
            };
            case IRRIGATION, HYDROPONICS -> switch (key) {
                case "freeChlorine" -> new Range(0, 1);
                case "totalAlkalinity" -> new Range(30, 100);
                default -> null;
            };
            default -> null;
        };
    }

    /** Latest value of each parameter among the given measurements (newest first), with its range for the place. */
    public static List<Reading> latest(PlaceKind kind, List<Measurement> newestFirst) {
        List<Reading> out = new ArrayList<>();
        for (Param p : PARAMS) {
            Range range = rangeFor(kind, p.key());
            Measurement found = newestFirst.stream().filter(m -> p.value().apply(m.getParams()) != null).findFirst().orElse(null);
            Double value = found == null ? null : p.value().apply(found.getParams());
            String status = value == null || range == null ? "UNKNOWN"
                : value < range.low() ? "LOW" : value > range.high() ? "HIGH" : "OK";
            out.add(new Reading(p.key(), p.label(), "mg/L", value, found == null ? null : found.getMeasuredAt(), range,
                status, advice(kind, p.key(), status)));
        }
        return out;
    }

    private static String advice(PlaceKind kind, String key, String status) {
        if ("OK".equals(status) || "UNKNOWN".equals(status)) {
            return null;
        }
        boolean low = "LOW".equals(status);
        return switch (key) {
            case "freeChlorine" -> kind == PlaceKind.AQUARIUM || kind == PlaceKind.POND
                ? "El cloro es tóxico para los peces: usa un acondicionador de agua antes de agregarla."
                : low ? "Poco cloro: la desinfección puede no ser suficiente." : "Mucho cloro: espera a que baje antes de usar el agua.";
            case "totalAlkalinity" -> low ? "Alcalinidad baja: el pH cambiará con facilidad." : "Alcalinidad alta: al pH le costará bajar.";
            case "calciumHardness" -> low ? "Agua blanda: puede corroer superficies y equipos." : "Agua dura: puede dejar incrustaciones.";
            case "cyanuricAcid" -> low ? "Poco estabilizador: el sol degrada el cloro rápido." : "Demasiado estabilizador: el cloro pierde eficacia.";
            default -> null;
        };
    }
}
