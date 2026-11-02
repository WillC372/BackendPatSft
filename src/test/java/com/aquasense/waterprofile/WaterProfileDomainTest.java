package com.aquasense.waterprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.shared.DomainException;
import com.aquasense.shared.spec.Specification;
import com.aquasense.waterprofile.domain.AcidityClass;
import com.aquasense.waterprofile.domain.PhRangeSpecification;
import com.aquasense.waterprofile.domain.RangeStatus;
import com.aquasense.waterprofile.domain.WaterProfile;
import com.aquasense.waterprofile.domain.WaterType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WaterProfileDomainTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private final WaterProfile pool = new WaterProfile(null, WaterType.POOL, "Piscina", "d",
            d("7.2"), d("7.8"), d("6.8"), d("8.2"), "CDC");

    @ParameterizedTest
    @CsvSource({"7.2,OPTIMAL", "7.5,OPTIMAL", "7.8,OPTIMAL", "7.0,CAUTION", "6.8,CAUTION", "8.2,CAUTION",
            "6.79,CRITICAL", "8.21,CRITICAL", "5.8,CRITICAL", "0,CRITICAL", "14,CRITICAL"})
    void classifiesStatusUsingProfileRanges(String ph, RangeStatus expected) {
        assertThat(pool.classify(d(ph)).status()).isEqualTo(expected);
    }

    @Test
    void sameValueClassifiesDifferentlyPerProfile() {
        WaterProfile drinking = new WaterProfile(null, WaterType.DRINKING, "Potable", "d",
                d("6.5"), d("8.5"), d("6.0"), d("9.0"), "OMS");
        assertThat(drinking.classify(d("8.3")).status()).isEqualTo(RangeStatus.OPTIMAL);
        assertThat(pool.classify(d("8.3")).status()).isEqualTo(RangeStatus.CRITICAL);
    }

    @ParameterizedTest
    @CsvSource({"3.0,ACIDIC", "6.49,ACIDIC", "6.5,NEUTRAL", "7.0,NEUTRAL", "7.5,NEUTRAL", "7.51,ALKALINE", "10,ALKALINE"})
    void classifiesAcidity(String ph, AcidityClass expected) {
        assertThat(pool.classify(d(ph)).acidity()).isEqualTo(expected);
    }

    @Test
    void rejectsValuesOutsideScale() {
        assertThatThrownBy(() -> pool.classify(d("14.1"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> pool.classify(d("-0.1"))).isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsInconsistentRanges() {
        assertThatThrownBy(() -> new WaterProfile(UUID.randomUUID(), WaterType.CUSTOM, "x", "d",
                d("7.8"), d("7.2"), d("6.8"), d("8.2"), null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new WaterProfile(UUID.randomUUID(), WaterType.CUSTOM, "x", "d",
                d("7.0"), d("7.5"), d("7.2"), d("8.0"), null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new WaterProfile(UUID.randomUUID(), WaterType.CUSTOM, "x", "d",
                d("7.0"), d("7.5"), d("6.0"), d("15"), null)).isInstanceOf(DomainException.class);
    }

    @Test
    void specificationsCompose() {
        Specification<BigDecimal> low = new PhRangeSpecification(d("0"), d("6.9"));
        Specification<BigDecimal> high = new PhRangeSpecification(d("7.1"), d("14"));
        Specification<BigDecimal> outsideNeutral = low.or(high);
        assertThat(outsideNeutral.isSatisfiedBy(d("5"))).isTrue();
        assertThat(outsideNeutral.isSatisfiedBy(d("7"))).isFalse();
        assertThat(outsideNeutral.not().isSatisfiedBy(d("7"))).isTrue();
        assertThat(low.and(high).isSatisfiedBy(d("5"))).isFalse();
    }

    @Test
    void copyCreatesIndependentCustomProfileOwnedByUser() {
        UUID owner = UUID.randomUUID();
        WaterProfile copy = pool.copyFor(owner, "Mi piscina");
        assertThat(copy.getId()).isNotEqualTo(pool.getId());
        assertThat(copy.getOwnerId()).isEqualTo(owner);
        assertThat(copy.getType()).isEqualTo(WaterType.CUSTOM);
        copy.update("Mi piscina", "d", d("7.0"), d("7.6"), d("6.5"), d("8.0"));
        assertThat(pool.getOptimalMin()).isEqualByComparingTo("7.2");
        assertThat(copy.getOptimalMin()).isEqualByComparingTo("7.0");
    }
}
