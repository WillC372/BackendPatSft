package com.aquasense.knowledge;

import com.aquasense.profile.ProfileType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Curated knowledge base of guidelines (classpath:knowledge/Guidelines.json). It is loaded and validated once at startup:
 * if a fragment points to a nonexistent source, repeats an id or has no quote, the application does not start.
 */
@Component
public class KnowledgeBase {

    static final String RESOURCE = "knowledge/Guidelines.json";

    private final String version;
    private final String note;
    private final Map<String, KnowledgeSource> sources = new LinkedHashMap<>();
    private final Map<String, KnowledgeChunk> chunks = new LinkedHashMap<>();
    private final Map<ProfileType, String> coverage = new EnumMap<>(ProfileType.class);

    @Autowired
    public KnowledgeBase(ObjectMapper mapper) {
        this(mapper, RESOURCE);
    }

    KnowledgeBase(ObjectMapper mapper, String resource) {
        JsonNode root;
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            root = mapper.readTree(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read knowledge base " + resource, ex);
        }
        version = root.path("version").asText();
        note = root.path("note").asText();
        for (JsonNode node : root.path("sources")) {
            KnowledgeSource source = mapper.convertValue(node, KnowledgeSource.class);
            require(sources.putIfAbsent(source.id(), source) == null, "Duplicated source id " + source.id());
        }
        for (JsonNode node : root.path("chunks")) {
            KnowledgeChunk chunk = mapper.convertValue(node, KnowledgeChunk.class);
            require(chunk.id() != null && !chunk.id().isBlank(), "Chunk without id");
            require(sources.containsKey(chunk.sourceId()), "Chunk " + chunk.id() + " points to unknown source " + chunk.sourceId());
            require(chunk.quote() != null && !chunk.quote().isBlank(), "Chunk " + chunk.id() + " has no quote");
            require(chunk.summaryEs() != null && !chunk.summaryEs().isBlank(), "Chunk " + chunk.id() + " has no summary");
            require(chunk.profileTypes() != null && !chunk.profileTypes().isEmpty(), "Chunk " + chunk.id() + " has no profile type");
            require(chunks.putIfAbsent(chunk.id(), withDefaults(chunk)) == null, "Duplicated chunk id " + chunk.id());
        }
        root.path("coverage").fields().forEachRemaining(e -> coverage.put(ProfileType.valueOf(e.getKey()), e.getValue().asText()));
        require(!chunks.isEmpty(), "Knowledge base is empty");
    }

    public String version() {
        return version;
    }

    public String note() {
        return note;
    }

    public List<KnowledgeSource> sources() {
        return List.copyOf(sources.values());
    }

    public List<KnowledgeChunk> chunks() {
        return List.copyOf(chunks.values());
    }

    public Optional<KnowledgeChunk> chunk(String id) {
        return Optional.ofNullable(id == null ? null : chunks.get(id));
    }

    public KnowledgeSource source(String id) {
        return sources.get(id);
    }

    public String coverage(ProfileType type) {
        return coverage.getOrDefault(type == null ? ProfileType.GENERAL : type, "");
    }

    public Map<ProfileType, String> coverage() {
        return Collections.unmodifiableMap(coverage);
    }

    public List<KnowledgeChunk> chunksOf(String sourceId) {
        List<KnowledgeChunk> list = new ArrayList<>();
        chunks.values().stream().filter(c -> c.sourceId().equals(sourceId)).forEach(list::add);
        return list;
    }

    private static KnowledgeChunk withDefaults(KnowledgeChunk c) {
        Set<KnowledgeChunk.Tag> tags = c.tags() == null || c.tags().isEmpty() ? EnumSet.noneOf(KnowledgeChunk.Tag.class)
            : EnumSet.copyOf(c.tags());
        return new KnowledgeChunk(c.id(), c.sourceId(), c.section(), c.url(), c.quote(), c.summaryEs(),
            EnumSet.copyOf(c.profileTypes()), tags, c.phMin(), c.phMax(), c.keywords());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
