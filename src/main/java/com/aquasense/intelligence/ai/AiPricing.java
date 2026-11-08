package com.aquasense.intelligence.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Computes the estimated cost of every call with the configured price of the model. The cost is stored with the call,
 * so a later price change does not rewrite the history.
 */
@Component
public class AiPricing {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiPricingProperties properties;

    public AiPricing(AiPricingProperties properties) {
        this.properties = properties;
    }

    public Optional<AiPricingProperties.ModelPrice> price(String model) {
        return properties.models().stream().filter(p -> p.model().equalsIgnoreCase(model == null ? "" : model)).findFirst();
    }

    public BigDecimal estimate(String model, long inputTokens, long outputTokens) {
        return price(model).map(p -> BigDecimal.valueOf(p.inputPerMillion()).multiply(BigDecimal.valueOf(inputTokens))
                .add(BigDecimal.valueOf(p.outputPerMillion()).multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 8, RoundingMode.HALF_UP))
            .orElse(BigDecimal.ZERO);
    }

    /** What is actually charged: 0 with the free plan of the providers. */
    public BigDecimal charged(BigDecimal estimated) {
        return isFreeBilling() ? BigDecimal.ZERO : estimated;
    }

    public boolean isFreeBilling() {
        return "FREE".equals(properties.billing());
    }

    public AiPricingProperties properties() {
        return properties;
    }
}
