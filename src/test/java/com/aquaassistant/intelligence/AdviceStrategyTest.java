package com.aquaassistant.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquaassistant.intelligence.advice.Advice;
import com.aquaassistant.intelligence.advice.AdviceStrategyResolver;
import com.aquaassistant.intelligence.advice.AquariumAdvice;
import com.aquaassistant.intelligence.advice.DrinkingWaterAdvice;
import com.aquaassistant.intelligence.advice.GeneralAdvice;
import com.aquaassistant.intelligence.advice.IrrigationAdvice;
import com.aquaassistant.intelligence.advice.PoolAdvice;
import com.aquaassistant.profile.PhClassifier;
import com.aquaassistant.profile.PhRange;
import com.aquaassistant.profile.ProfileType;
import com.aquaassistant.profile.WaterProfile;
import com.aquaassistant.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdviceStrategyTest {

    private final AdviceStrategyResolver resolver = new AdviceStrategyResolver(List.of(new GeneralAdvice(),
        new DrinkingWaterAdvice(), new PoolAdvice(), new AquariumAdvice(), new IrrigationAdvice()));
    private final PhClassifier classifier = new PhClassifier();

    @Test
    void poolLowPhRecommendsIncreaser() {
        WaterProfile pool = TestData.pool();
        Advice advice = resolver.forType(ProfileType.POOL).advise(classifier.classify(6.9, pool), pool);
        assertThat(advice.actions()).anyMatch(a -> a.contains("incrementador"));
        assertThat(advice.needsExpert()).isTrue();
    }

    @Test
    void drinkingWaterAlwaysWarnsAboutPotability() {
        WaterProfile drinking = new WaterProfile(null, "Agua potable", ProfileType.DRINKING,
            new PhRange(6.5, 8.5, 6.0, 9.0), "Potable", "OMS");
        Advice optimal = resolver.forType(ProfileType.DRINKING).advise(classifier.classify(7.2, drinking), drinking);
        assertThat(optimal.actions().get(0)).contains("no demuestra que el agua sea potable");
    }

    @Test
    void customProfilesFallBackToGeneralStrategy() {
        assertThat(resolver.forType(ProfileType.CUSTOM)).isInstanceOf(GeneralAdvice.class);
    }
}
