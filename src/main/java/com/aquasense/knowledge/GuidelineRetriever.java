package com.aquasense.knowledge;

import com.aquasense.profile.Classification;
import com.aquasense.profile.PhStatus;
import com.aquasense.profile.ProfileType;
import com.aquasense.profile.WaterProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Recuperación de fragmentos normativos (la "R" de RAG). Combina BM25 sobre el texto de cada fragmento con dos
 * señales de dominio: las etiquetas de tema que pide el estado del agua y si el rango del fragmento habla del valor
 * medido. Solo devuelve fragmentos aplicables al tipo de perfil, así una norma de piscinas nunca respalda un consejo de riego.
 */
@Service
public class GuidelineRetriever {

    static final double TEXT_WEIGHT = 0.6;
    static final double TAG_WEIGHT = 0.3;
    static final double RANGE_BONUS = 0.1;

    private final KnowledgeBase knowledgeBase;
    private final List<KnowledgeChunk> chunks;
    private final Bm25Index index;

    public GuidelineRetriever(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
        this.chunks = knowledgeBase.chunks();
        this.index = new Bm25Index(chunks.stream().map(KnowledgeChunk::searchableText).toList());
    }

    /** Búsqueda libre (chat, agente, biblioteca). {@code type} nulo busca en toda la base. */
    public List<Citation> search(String query, ProfileType type, Set<KnowledgeChunk.Tag> preferred, Double ph, int limit) {
        Set<KnowledgeChunk.Tag> tags = preferred == null ? Set.of() : preferred;
        double[] text = index.score(query == null ? "" : query);
        double max = 0;
        for (double s : text) {
            max = Math.max(max, s);
        }
        List<Citation> results = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            if (type != null && !chunk.profileTypes().contains(type)) {
                continue;
            }
            double textScore = max > 0 ? text[i] / max : 0;
            long shared = tags.stream().filter(chunk.tags()::contains).count();
            double tagScore = tags.isEmpty() ? 0 : (double) shared / tags.size();
            boolean speaksToValue = ph != null && (chunk.phMin() != null || chunk.phMax() != null);
            double score = TEXT_WEIGHT * textScore + TAG_WEIGHT * tagScore + (speaksToValue ? RANGE_BONUS : 0);
            if (textScore > 0 || shared > 0) {
                results.add(Citation.of(chunk, knowledgeBase.source(chunk.sourceId()), score));
            }
        }
        results.sort(Comparator.comparingDouble(Citation::score).reversed().thenComparing(Citation::id));
        return results.subList(0, Math.min(Math.max(limit, 0), results.size()));
    }

    /** Fragmentos que respaldan la interpretación de una medición para un perfil. */
    public List<Citation> forReading(WaterProfile profile, Classification reading, int limit) {
        ProfileType type = profile.getType();
        if (reading == null) {
            return search("rango recomendado medir frecuencia", type, EnumSet.of(KnowledgeChunk.Tag.RANGE), null, limit);
        }
        Set<KnowledgeChunk.Tag> tags = EnumSet.of(KnowledgeChunk.Tag.RANGE);
        StringBuilder query = new StringBuilder("rango recomendado ").append(type.label());
        if (reading.status() != PhStatus.OPTIMAL) {
            boolean low = reading.ph() < profile.getIdealMin();
            tags.add(low ? KnowledgeChunk.Tag.LOW : KnowledgeChunk.Tag.HIGH);
            tags.add(KnowledgeChunk.Tag.ACTION);
            query.append(low ? " pH bajo ácido corrosión corrosivo subir corregir" : " pH alto alcalino depósitos incrustaciones bajar corregir");
        }
        if (reading.status() == PhStatus.CRITICAL) {
            tags.add(KnowledgeChunk.Tag.CRITICAL);
            tags.add(KnowledgeChunk.Tag.HEALTH);
            query.append(" riesgo salud laboratorio cierre");
        }
        return search(query.toString(), type, tags, reading.ph(), limit);
    }

    public String coverage(ProfileType type) {
        return knowledgeBase.coverage(type);
    }

    /** Resuelve ids citados por la IA. Los ids desconocidos se descartan; nunca se inventa un fragmento. */
    public List<Citation> resolve(Collection<String> ids) {
        List<Citation> citations = new ArrayList<>();
        for (String id : new LinkedHashSet<>(ids == null ? List.<String>of() : ids)) {
            knowledgeBase.chunk(id).ifPresent(c -> citations.add(Citation.of(c, knowledgeBase.source(c.sourceId()), 0)));
        }
        return citations;
    }

    public boolean exists(String id) {
        return knowledgeBase.chunk(id).isPresent();
    }
}
