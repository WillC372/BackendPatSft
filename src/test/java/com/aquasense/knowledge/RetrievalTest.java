package com.aquasense.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.profile.Classification;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.PhRange;
import com.aquasense.profile.ProfileType;
import com.aquasense.profile.WaterProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetrievalTest {

    private final GuidelineRetriever retriever = new GuidelineRetriever(new KnowledgeBase(new ObjectMapper()));
    private final PhClassifier classifier = new PhClassifier();

    private static WaterProfile profile(ProfileType type, double idealMin, double idealMax, double cautionMin, double cautionMax) {
        return new WaterProfile(null, type.label(), type, new PhRange(idealMin, idealMax, cautionMin, cautionMax), "", "");
    }

    @Test
    void analyzerFoldsAccentsGroupsVariantsAndKeepsDecimals() {
        assertThat(TextAnalyzer.terms("Corrosión de las tuberías")).containsExactly("corros", "tuberi");
        assertThat(TextAnalyzer.terms("corrosive pipes")).containsExactly("corros", "pipes");
        assertThat(TextAnalyzer.terms("rango 6,5 a 8.4")).containsExactly("rango", "6.5", "8.4");
        assertThat(TextAnalyzer.terms("el agua y el pH")).isEmpty();
    }

    @Test
    void bm25RanksTheDocumentThatSharesRareTermsFirst() {
        Bm25Index index = new Bm25Index(List.of("cloro piscina", "corrosión tuberías cobre", "riego cultivos suelo"));
        double[] scores = index.score("¿Por qué se corroen las tuberías?");
        assertThat(scores[1]).isGreaterThan(0);
        assertThat(scores[0]).isZero();
        assertThat(scores[2]).isZero();
    }

    @Test
    void lowPoolReadingIsBackedOnlyByPoolSources() {
        WaterProfile pool = profile(ProfileType.POOL, 7.0, 7.8, 6.5, 8.0);
        Classification low = classifier.classify(6.3, pool);
        List<Citation> sources = retriever.forReading(pool, low, 4);
        assertThat(sources).isNotEmpty().allSatisfy(c -> assertThat(c.sourceId()).isEqualTo("cdc-mahc-2024"));
        assertThat(sources).extracting(Citation::id).contains("mahc-ph-range", "mahc-imminent-hazard");
    }

    @Test
    void highIrrigationReadingUsesFaoAndNeverAPoolNorm() {
        WaterProfile irrigation = profile(ProfileType.IRRIGATION, 6.5, 8.4, 6.0, 8.5);
        List<Citation> sources = retriever.forReading(irrigation, classifier.classify(8.45, irrigation), 3);
        assertThat(sources).isNotEmpty().allSatisfy(c -> assertThat(c.sourceId()).isEqualTo("fao-ipd29"));
        assertThat(sources.get(0).id()).isIn("fao-ph-normal", "fao-table1", "fao-soil-correction");
    }

    @Test
    void freeSearchFindsTheRelevantFragmentAndAquariumReturnsNothing() {
        List<Citation> corrosion = retriever.search("mis tuberías de cobre se manchan", ProfileType.DRINKING, null, null, 3);
        assertThat(corrosion.get(0).id()).isEqualTo("epa-corrosion");
        List<Citation> frequency = retriever.search("cada cuántas horas debo medir", ProfileType.POOL, null, null, 2);
        assertThat(frequency.get(0).id()).isEqualTo("mahc-testing");
        WaterProfile aquarium = profile(ProfileType.AQUARIUM, 6.5, 7.5, 6.0, 8.0);
        assertThat(retriever.forReading(aquarium, classifier.classify(5.5, aquarium), 3)).isEmpty();
        assertThat(retriever.coverage(ProfileType.AQUARIUM)).isNotBlank();
    }

    @Test
    void resolveKeepsOnlyExistingFragments() {
        assertThat(retriever.resolve(List.of("fao-table2", "inventado-1", "fao-table2"))).extracting(Citation::id)
            .containsExactly("fao-table2");
    }

    @Test
    void chatReferencesAreKeptOnlyWhenTheyWereProvided() {
        CitationReferences.Result result = CitationReferences.filter(
            "El rango es 6.5-8.5 [epa-ph-smcl]. Según otra norma [who-ph-xx], es seguro.", Set.of("epa-ph-smcl"));
        assertThat(result.citedIds()).containsExactly("epa-ph-smcl");
        assertThat(result.removedIds()).containsExactly("who-ph-xx");
        assertThat(result.text()).isEqualTo("El rango es 6.5-8.5 [epa-ph-smcl]. Según otra norma, es seguro.");
    }
}
