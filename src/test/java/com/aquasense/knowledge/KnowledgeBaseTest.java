package com.aquasense.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.profile.ProfileType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeBaseTest {

    private final KnowledgeBase kb = new KnowledgeBase(new ObjectMapper());

    @Test
    void loadsCuratedSourcesWithValidReferences() {
        assertThat(kb.sources()).extracting(KnowledgeSource::id)
            .containsExactlyInAnyOrder("epa-sdws", "who-gdwq-ph", "cdc-mahc-2024", "fao-ipd29");
        Set<String> ids = new HashSet<>();
        for (KnowledgeChunk chunk : kb.chunks()) {
            assertThat(ids.add(chunk.id())).as("id único " + chunk.id()).isTrue();
            assertThat(kb.source(chunk.sourceId())).isNotNull();
            assertThat(chunk.url()).startsWith("https://");
            assertThat(chunk.quote()).isNotBlank().isNotEqualTo(chunk.summaryEs());
        }
        assertThat(kb.sources()).allSatisfy(s -> assertThat(s.legalStatus()).isNotBlank());
    }

    @Test
    void everyProfileTypeHasCoverageAndAquariumHasNoInventedNorm() {
        for (ProfileType type : ProfileType.values()) {
            assertThat(kb.coverage(type)).as("cobertura " + type).isNotBlank();
        }
        assertThat(kb.chunks()).noneMatch(c -> c.profileTypes().contains(ProfileType.AQUARIUM));
        assertThat(kb.coverage(ProfileType.AQUARIUM)).contains("no contiene una norma oficial verificada");
        for (ProfileType type : new ProfileType[] {ProfileType.GENERAL, ProfileType.DRINKING, ProfileType.POOL, ProfileType.IRRIGATION}) {
            assertThat(kb.chunks()).as("fragmentos para " + type).anyMatch(c -> c.profileTypes().contains(type));
        }
    }

    @Test
    void rangesMatchTheOfficialTexts() {
        assertThat(kb.chunk("mahc-ph-range")).get().satisfies(c -> {
            assertThat(c.quote()).contains("7.0 - 7.8");
            assertThat(c.phMin()).isEqualTo(7.0);
            assertThat(c.phMax()).isEqualTo(7.8);
        });
        assertThat(kb.chunk("fao-ph-normal")).get().satisfies(c -> assertThat(c.quote()).contains("from 6.5 to 8.4"));
        assertThat(kb.chunk("epa-ph-smcl")).get().satisfies(c -> assertThat(c.quote()).contains("6.5 - 8.5"));
        assertThat(kb.chunk("no-existe")).isEmpty();
    }
}
