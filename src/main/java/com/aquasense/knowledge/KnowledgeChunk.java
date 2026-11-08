package com.aquasense.knowledge;

import com.aquasense.profile.ProfileType;
import java.util.Set;

/**
 * Fragmento citable: {@code quote} es el texto literal del documento (en su idioma original) y
 * {@code summaryEs} es un resumen propio en español. Nunca se muestra el resumen como si fuera la cita.
 */
public record KnowledgeChunk(String id, String sourceId, String section, String url, String quote, String summaryEs,
                             Set<ProfileType> profileTypes, Set<Tag> tags, Double phMin, Double phMax, String keywords) {

    /** Etiquetas de tema que permiten priorizar fragmentos según el estado del agua. */
    public enum Tag { RANGE, LOW, HIGH, CRITICAL, ACTION, TESTING, HEALTH, LAB }

    /** Texto que se indexa: cita, resumen, sección y palabras clave en español. */
    String searchableText() {
        return String.join(" ", section, quote, summaryEs, keywords == null ? "" : keywords);
    }
}
