package com.aquasense.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.common.BusinessException;
import com.aquasense.support.TestData;
import org.junit.jupiter.api.Test;

class PhClassifierTest {

    private final PhClassifier classifier = new PhClassifier();
    private final WaterProfile pool = TestData.pool();

    @Test
    void classifiesAgainstProfileRanges() {
        assertThat(classifier.classify(7.4, pool).status()).isEqualTo(PhStatus.OPTIMAL);
        assertThat(classifier.classify(7.1, pool).status()).isEqualTo(PhStatus.CAUTION);
        assertThat(classifier.classify(6.5, pool).status()).isEqualTo(PhStatus.CRITICAL);
        assertThat(classifier.classify(8.3, pool).status()).isEqualTo(PhStatus.CRITICAL);
    }

    @Test
    void sameValueGetsDifferentStatusPerProfile() {
        WaterProfile irrigation = new WaterProfile(null, "Riego", ProfileType.IRRIGATION,
            new PhRange(6.0, 7.5, 5.5, 8.4), "Riego", "FAO");
        assertThat(classifier.classify(6.5, irrigation).status()).isEqualTo(PhStatus.OPTIMAL);
        assertThat(classifier.classify(6.5, pool).status()).isEqualTo(PhStatus.CRITICAL);
    }

    @Test
    void classifiesChemicalNature() {
        assertThat(PhNature.of(5.0)).isEqualTo(PhNature.ACIDIC);
        assertThat(PhNature.of(7.0)).isEqualTo(PhNature.NEUTRAL);
        assertThat(PhNature.of(8.5)).isEqualTo(PhNature.ALKALINE);
    }

    @Test
    void prototypeCopyKeepsRangesAndChangesOwner() {
        WaterProfile copy = pool.copyFor(42L, "Mi piscina");
        assertThat(copy.getOwnerId()).isEqualTo(42L);
        assertThat(copy.getName()).isEqualTo("Mi piscina");
        assertThat(copy.ranges()).isEqualTo(pool.ranges());
        assertThat(copy.isSystemProfile()).isFalse();
        assertThat(copy).isNotSameAs(pool);
    }

    @Test
    void rejectsInvalidRangeOrder() {
        assertThatThrownBy(() -> new PhRange(7.8, 7.2, 7.0, 8.0).validate()).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new PhRange(7.0, 8.0, -1, 8.0).validate()).isInstanceOf(BusinessException.class);
    }
}
