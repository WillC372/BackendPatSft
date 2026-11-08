package com.aquasense.knowledge;

/**
 * Retrieved fragment ready to be shown next to a recommendation, a diagnosis, a report or the chat.
 */
public record Citation(String id, String sourceId, String organization, String title, String edition, String section,
                       String quote, String quoteLanguage, String summaryEs, String url, String legalStatus, double score) {

    static Citation of(KnowledgeChunk chunk, KnowledgeSource source, double score) {
        return new Citation(chunk.id(), source.id(), source.organization(), source.title(), source.edition(), chunk.section(),
            chunk.quote(), source.language(), chunk.summaryEs(), chunk.url(), source.legalStatus(),
            Math.round(score * 1000) / 1000.0);
    }

    /** Short reference to show in the text, for example "CDC (Estados Unidos), 5.7.3.4.1 pH Range". */
    public String shortReference() {
        return organization + ", " + section;
    }
}
