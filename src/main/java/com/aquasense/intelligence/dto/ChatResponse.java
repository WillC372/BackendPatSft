package com.aquasense.intelligence.dto;

import com.aquasense.knowledge.Citation;
import java.util.List;

/**
 * Respuesta del asistente. {@code citations} contiene solo los fragmentos que la IA citó con [id] y que el sistema
 * le entregó; {@code removedReferences} cuenta las referencias inexistentes que se borraron del texto.
 * {@code memoryTurns} son los mensajes previos que se enviaron como contexto y {@code omittedTurns} los que no cupieron.
 */
public record ChatResponse(String answer, String model, List<Citation> citations, int removedReferences,
                           Long conversationId, String conversationTitle, int memoryTurns, int omittedTurns) {
}
