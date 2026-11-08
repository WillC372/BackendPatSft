package com.aquasense.intelligence.ai;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Precios por millón de tokens (USD) para estimar costos. {@code billing} indica cómo se factura la cuenta:
 * FREE (plan gratuito, cargo real 0) o PAID. {@code verified} distingue un precio leído en la página oficial
 * del proveedor de una referencia de terceros; la interfaz lo muestra.
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
