package com.aquasense.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CitationReferencesTest {

    private static final Set<String> DELIVERED = Set.of("mahc-ph-range", "epa-ph-smcl");

    @Test
    void referencesInParenthesesOrGroupedBecomeOneMarkEach() {
        var result = CitationReferences.filter("Entre 7.0 y 7.8 (mahc-ph-range). Ver [epa-ph-smcl, mahc-ph-range].", DELIVERED);
        assertThat(result.text()).isEqualTo("Entre 7.0 y 7.8 [mahc-ph-range]. Ver [epa-ph-smcl][mahc-ph-range].");
        assertThat(result.citedIds()).containsExactly("mahc-ph-range", "epa-ph-smcl");
    }

    @Test
    void ordinaryParenthesesAndUnknownIdsAreNotTurnedIntoCitations() {
        var result = CitationReferences.filter("Unidades (mg-l) y una cita inventada [who-fake-1] (otra-fuente-9).", DELIVERED);
        assertThat(result.text()).isEqualTo("Unidades (mg-l) y una cita inventada (otra-fuente-9).");
        assertThat(result.citedIds()).isEmpty();
        assertThat(result.removedIds()).containsExactly("who-fake-1");
    }
}
