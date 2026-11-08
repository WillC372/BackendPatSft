package com.aquasense.intelligence.dto;

import com.aquasense.knowledge.Citation;
import java.util.List;

/**
 * Assistant answer. {@code citations} only contains the fragments the AI cited with [id] and that the system
 * provided; {@code removedReferences} counts the nonexistent references removed from the text.
 * {@code memoryTurns} are the previous messages sent as context and {@code omittedTurns} those that did not fit.
 */
public record ChatResponse(String answer, String model, List<Citation> citations, int removedReferences,
                           Long conversationId, String conversationTitle, int memoryTurns, int omittedTurns) {
}
