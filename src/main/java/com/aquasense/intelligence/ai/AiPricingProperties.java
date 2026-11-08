package com.aquasense.intelligence.ai;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prices per million tokens (USD) to estimate costs. {@code billing} tells how the account is billed:
 * FREE (free plan, real charge 0) or PAID. {@code verified} distinguishes a price read on the official page
 * of the provider from a third-party reference; the UI shows it.
 */
@ConfigurationProperties(prefix = "aquasense.ai-pricing")
public record AiPricingProperties(String billing, String note, List<ModelPrice> models) {

    public AiPricingProperties {
        billing = billing == null || billing.isBlank() ? "FREE" : billing.trim().toUpperCase(java.util.Locale.ROOT);
        models = models == null ? List.of() : List.copyOf(models);
    }

    public record ModelPrice(String model, double inputPerMillion, double outputPerMillion, String source, String checkedOn,
                             boolean verified) {
    }
}
