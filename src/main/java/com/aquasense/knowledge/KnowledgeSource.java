package com.aquasense.knowledge;

/**
 * Official document the fragments of the knowledge base come from.
 */
public record KnowledgeSource(String id, String organization, String title, String edition, String url,
                              String retrievedOn, String language, String legalStatus) {
}
