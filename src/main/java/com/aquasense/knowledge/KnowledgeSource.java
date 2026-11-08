package com.aquasense.knowledge;

/**
 * Documento oficial del que provienen los fragmentos de la base de conocimiento.
 */
public record KnowledgeSource(String id, String organization, String title, String edition, String url,
                              String retrievedOn, String language, String legalStatus) {
}
