package com.aquasense.intelligence.ai;

import com.aquasense.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seguimiento de costos de IA: agrega el registro de llamadas por modelo, por función y por día.
 */
@Service
public class AiUsageService {

    static final int MAX_DAYS = 90;
    /** Llamadas registradas antes de la migración V7, sin proveedor, latencia ni costo. */
    static final String LEGACY = "Registro anterior al seguimiento de costos";
    private static final Map<String, String> PURPOSES = Map.of(
        "DIAGNOSIS", "Diagnóstico", "CHAT", "Asistente", "CONCLUSION", "Conclusión", "AGENT_REPORT", "Agente de informes",
        "PHOTO_READING", "Lectura por foto", "SCENARIO", "Simulador de escenarios");

    private final AiUsageRepository repository;
    private final AiProviderProxy proxy;
    private final UserRepository userRepository;

    public AiUsageService(AiUsageRepository repository, AiProviderProxy proxy, UserRepository userRepository) {
        this.repository = repository;
        this.proxy = proxy;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AiUsageReport forUser(Long userId, int days) {
        int period = clamp(days);
        List<AiUsage> rows = repository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, since(period));
        return build(period, rows, List.of(), proxy.usedToday(userId), proxy.quotaFor(userId));
    }

    @Transactional(readOnly = true)
    public AiUsageReport global(int days) {
        int period = clamp(days);
        List<AiUsage> rows = repository.findByCreatedAtAfterOrderByCreatedAtAsc(since(period));
        Map<Long, List<AiUsage>> byUser = rows.stream().collect(Collectors.groupingBy(AiUsage::getUserId));
        List<AiUsageReport.UserRow> users = byUser.entrySet().stream().map(e -> new AiUsageReport.UserRow(e.getKey(),
                userRepository.findById(e.getKey()).map(u -> u.getEmail()).orElse("(eliminado)"), successful(e.getValue()).size(),
                tokens(e.getValue()), cost(e.getValue())))
            .sorted(Comparator.comparing(AiUsageReport.UserRow::estimatedUsd).reversed()
                .thenComparing(Comparator.comparingLong(AiUsageReport.UserRow::calls).reversed()))
            .limit(10).toList();
        return build(period, rows, users, null, null);
    }

    AiUsageReport build(int period, List<AiUsage> rows, List<AiUsageReport.UserRow> users, Long usedToday, Integer quota) {
        AiPricing pricing = proxy.pricing();
        List<AiUsage> ok = successful(rows);
        long failures = rows.size() - ok.size();
        BigDecimal estimated = cost(rows);
        AiUsageReport.Totals totals = new AiUsageReport.Totals(ok.stream().filter(u -> !u.isCached()).count(),
            ok.stream().filter(AiUsage::isCached).count(), failures,
            rows.stream().mapToLong(AiUsage::getInputTokens).sum(), rows.stream().mapToLong(AiUsage::getOutputTokens).sum(),
            estimated, pricing.charged(estimated), avgLatency(ok.stream().filter(u -> !u.isCached()).toList()),
            rows.isEmpty() ? 0 : Math.round(1000.0 * failures / rows.size()) / 1000.0);

        Map<String, List<AiUsage>> byModel = group(rows.stream().filter(u -> !u.isCached()).toList(),
            u -> (u.getProvider() == null ? LEGACY : u.getProvider()) + "|" + u.getModel());
        List<AiUsageReport.ModelRow> models = new ArrayList<>();
        byModel.forEach((key, list) -> {
            String[] parts = key.split("\\|", 2);
            var price = pricing.price(parts[1]);
            String lastError = list.stream().filter(u -> !u.isSuccess()).reduce((a, b) -> b).map(AiUsage::getError).orElse(null);
            models.add(new AiUsageReport.ModelRow(parts[0], parts[1], successful(list).size(), list.size() - successful(list).size(),
                list.stream().mapToLong(AiUsage::getInputTokens).sum(), list.stream().mapToLong(AiUsage::getOutputTokens).sum(),
                cost(list), avgLatency(successful(list)), price.map(AiPricingProperties.ModelPrice::inputPerMillion).orElse(null),
                price.map(AiPricingProperties.ModelPrice::outputPerMillion).orElse(null),
                price.map(AiPricingProperties.ModelPrice::verified).orElse(null),
                price.map(AiPricingProperties.ModelPrice::source).orElse(null), lastError));
        });
        models.sort(Comparator.comparing(AiUsageReport.ModelRow::estimatedUsd).reversed()
            .thenComparing(Comparator.comparingLong(AiUsageReport.ModelRow::calls).reversed()));

        List<AiUsageReport.PurposeRow> purposes = group(ok, AiUsage::getPurpose).entrySet().stream()
            .map(e -> new AiUsageReport.PurposeRow(e.getKey(), PURPOSES.getOrDefault(e.getKey(), e.getKey()),
                e.getValue().stream().filter(u -> !u.isCached()).count(), tokens(e.getValue()), cost(e.getValue())))
            .sorted(Comparator.comparingLong(AiUsageReport.PurposeRow::calls).reversed()).toList();

        Map<LocalDate, List<AiUsage>> perDay = new TreeMap<>(rows.stream()
            .collect(Collectors.groupingBy(u -> LocalDate.ofInstant(u.getCreatedAt(), ZoneOffset.UTC))));
        List<AiUsageReport.DayRow> daily = new ArrayList<>();
        LocalDate first = LocalDate.now(ZoneOffset.UTC).minusDays(period - 1L);
        for (LocalDate d = first; !d.isAfter(LocalDate.now(ZoneOffset.UTC)); d = d.plusDays(1)) {
            List<AiUsage> list = perDay.getOrDefault(d, List.of());
            daily.add(new AiUsageReport.DayRow(d, successful(list).stream().filter(u -> !u.isCached()).count(),
                list.size() - successful(list).size(), tokens(list), cost(list)));
        }
        return new AiUsageReport(period, pricing.properties().billing(), pricing.properties().note(), totals, models, purposes,
            daily, users, usedToday, quota);
    }

    private static List<AiUsage> successful(List<AiUsage> rows) {
        return rows.stream().filter(AiUsage::isSuccess).toList();
    }

    private static long tokens(List<AiUsage> rows) {
        return rows.stream().mapToLong(u -> u.getInputTokens() + u.getOutputTokens()).sum();
    }

    private static BigDecimal cost(List<AiUsage> rows) {
        return rows.stream().map(AiUsage::getCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(6, RoundingMode.HALF_UP);
    }

    private static Long avgLatency(List<AiUsage> rows) {
        List<Long> values = rows.stream().map(AiUsage::getLatencyMs).filter(v -> v != null && v > 0).toList();
        return values.isEmpty() ? null : Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static <K> Map<K, List<AiUsage>> group(List<AiUsage> rows, Function<AiUsage, K> key) {
        return rows.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private static int clamp(int days) {
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    private static Instant since(int days) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
