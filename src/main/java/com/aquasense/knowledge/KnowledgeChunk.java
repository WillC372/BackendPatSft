package com.aquasense.knowledge;

import com.aquasense.profile.ProfileType;
import java.util.Set;

/**
 * Citable fragment: {@code quote} is the literal text of the document (in its original language) and
 * {@code summaryEs} is our own summary in Spanish. The summary is never shown as if it were the quote.
 */
public record KnowledgeChunk(String id, String sourceId, String section, String url, String quote, String summaryEs,
                             Set<ProfileType> profileTypes, Set<Tag> tags, Double phMin, Double phMax, String keywords) {

    /** Topic tags that allow prioritizing fragments according to the water state. */
    public enum Tag { RANGE, LOW, HIGH, CRITICAL, ACTION, TESTING, HEALTH, LAB }

    /** Indexed text: quote, summary, section and keywords in Spanish. */
    String searchableText() {
        return String.join(" ", section, quote, summaryEs, keywords == null ? "" : keywords);
    }
}
