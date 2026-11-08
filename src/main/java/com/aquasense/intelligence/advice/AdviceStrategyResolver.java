package com.aquasense.intelligence.advice;

import com.aquasense.profile.ProfileType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Selects the strategy for the profile type. Custom profiles use the general strategy.
 */
@Component
public class AdviceStrategyResolver {

    private final Map<ProfileType, AdviceStrategy> strategies = new EnumMap<>(ProfileType.class);

    public AdviceStrategyResolver(List<AdviceStrategy> available) {
        available.forEach(strategy -> strategies.put(strategy.type(), strategy));
    }

    public AdviceStrategy forType(ProfileType type) {
        return strategies.getOrDefault(type, strategies.get(ProfileType.GENERAL));
    }
}
