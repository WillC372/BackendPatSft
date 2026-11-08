package com.aquasense.intelligence.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen de consumo de IA de un periodo: totales, por modelo, por función, por día y (solo administración) por usuario.
 * {@code estimatedUsd} es lo que costaría con facturación; {@code chargedUsd} es el cargo real según el modo de facturación.
 */
public record AiUsageReport(int periodDays, String billing, String pricingNote, Totals totals, List<ModelRow> byModel,
                            List<PurposeRow> byPurpose, List<DayRow> daily, List<UserRow> topUsers, Long usedToday,
                            Integer dailyQuota) {

    public record Totals(long calls, long cachedHits, long failures, long inputTokens, long outputTokens,
                         BigDecimal estimatedUsd, BigDecimal chargedUsd, Long avgLatencyMs, double failureRate) {
    }

    public record ModelRow(String provider, String model, long calls, long failures, long inputTokens, long outputTokens,
                           BigDecimal estimatedUsd, Long avgLatencyMs, Double inputPerMillion, Double outputPerMillion,
                           Boolean priceVerified, String priceSource, String lastError) {
    }

    public record PurposeRow(String purpose, String label, long calls, long tokens, BigDecimal estimatedUsd) {
    }

    public record DayRow(LocalDate date, long calls, long failures, long tokens, BigDecimal estimatedUsd) {
    }

    public record UserRow(Long userId, String email, long calls, long tokens, BigDecimal estimatedUsd) {
    }
}
