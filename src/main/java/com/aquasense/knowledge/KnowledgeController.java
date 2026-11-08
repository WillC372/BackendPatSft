package com.aquasense.knowledge;

import com.aquasense.common.BusinessException;
import com.aquasense.profile.ProfileType;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guidelines library: sources, search and individual fragments. Read only.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private static final int MAX_RESULTS = 10;

    private final KnowledgeBase knowledgeBase;
    private final GuidelineRetriever retriever;

    public KnowledgeController(KnowledgeBase knowledgeBase, GuidelineRetriever retriever) {
        this.knowledgeBase = knowledgeBase;
        this.retriever = retriever;
    }

    @GetMapping("/sources")
    public LibraryResponse sources() {
        List<LibraryResponse.SourceEntry> entries = knowledgeBase.sources().stream().map(s -> {
            List<KnowledgeChunk> chunks = knowledgeBase.chunksOf(s.id());
            EnumSet<ProfileType> types = EnumSet.noneOf(ProfileType.class);
            chunks.forEach(c -> types.addAll(c.profileTypes()));
            types.remove(ProfileType.CUSTOM);
            return new LibraryResponse.SourceEntry(s, chunks.size(), List.copyOf(types),
                chunks.stream().map(c -> retriever.resolve(List.of(c.id())).get(0)).toList());
        }).toList();
        return new LibraryResponse(knowledgeBase.version(), knowledgeBase.note(), entries, knowledgeBase.coverage());
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam("q") String query,
                                 @RequestParam(value = "profileType", required = false) ProfileType type,
                                 @RequestParam(value = "limit", defaultValue = "5") int limit) {
        if (query == null || query.isBlank()) {
            throw BusinessException.badRequest("Escribe qué quieres buscar.");
        }
        if (query.length() > 200) {
            throw BusinessException.badRequest("La búsqueda admite como máximo 200 caracteres.");
        }
        List<Citation> results = retriever.search(query, type, null, null, Math.min(Math.max(limit, 1), MAX_RESULTS));
        return new SearchResponse(query.trim(), type, results, type == null ? null : retriever.coverage(type));
    }

    @GetMapping("/chunks/{id}")
    public Citation chunk(@PathVariable String id) {
        List<Citation> found = retriever.resolve(List.of(id));
        if (found.isEmpty()) {
            throw BusinessException.notFound("No existe el fragmento " + id + ".");
        }
        return found.get(0);
    }

    public record LibraryResponse(String version, String note, List<SourceEntry> sources, Map<ProfileType, String> coverage) {

        public record SourceEntry(KnowledgeSource source, int chunkCount, List<ProfileType> profileTypes,
                                  List<Citation> chunks) {
        }
    }

    public record SearchResponse(String query, ProfileType profileType, List<Citation> results, String coverageNote) {
    }
}
