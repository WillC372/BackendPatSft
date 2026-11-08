package com.aquasense.intelligence.ai;

import com.aquasense.common.BusinessException;
import com.aquasense.config.AppProperties;
import com.aquasense.user.Plan;
import com.aquasense.user.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Proxy: se interpone entre la aplicación y el proveedor real de IA para controlar
 * disponibilidad, cuota diaria por plan (costos), cache de respuestas y registro de consumo.
 * La cache usa un Read-Write Lock: muchas lecturas concurrentes, escrituras exclusivas.
 */
@Primary
@Component
public class AiProviderProxy implements AiTextProvider, AiCallListener {

    private final AiTextProvider realProvider;
    private final AiUsageRepository usageRepository;
    private final UserRepository userRepository;
    private final AppProperties.Ai config;
    private final AiPricing pricing;
    private final AiUsageRecorder recorder;
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    @org.springframework.beans.factory.annotation.Autowired
    public AiProviderProxy(FallbackAiProvider realProvider, AiUsageRepository usageRepository,
                           UserRepository userRepository, AppProperties properties, AiPricing pricing,
                           AiUsageRecorder recorder) {
        this((AiTextProvider) realProvider, usageRepository, userRepository, properties, pricing, recorder);
        realProvider.setListener(this);
    }

    AiProviderProxy(AiTextProvider realProvider, AiUsageRepository usageRepository, UserRepository userRepository,
                    AppProperties properties, AiPricing pricing, AiUsageRecorder recorder) {
        this.realProvider = realProvider;
        this.usageRepository = usageRepository;
        this.userRepository = userRepository;
        this.config = properties.ai();
        this.pricing = pricing;
        this.recorder = recorder;
    }

    @Override
    public void onFailure(AiPrompt prompt, String provider, String model, long latencyMs, String error) {
        if (prompt.userId() != null) {
            recorder.save(AiUsage.failure(prompt.userId(), prompt.purpose(), provider, model, latencyMs, error));
        }
    }

    /** Registra un proveedor que falló durante una tarea de varios turnos (agente). */
    public void recordFailure(Long userId, String purpose, String provider, String model, long latencyMs, String error) {
        recorder.save(AiUsage.failure(userId, purpose, provider, model, latencyMs, error));
    }

    public AiPricing pricing() {
        return pricing;
    }

    @Override
    public boolean isAvailable() {
        return realProvider.isAvailable();
    }

    @Override
    public String providerName() {
        return realProvider.providerName();
    }

    public List<AiTextProvider> chain() {
        return realProvider instanceof FallbackAiProvider fallback ? fallback.chain() : List.of(realProvider);
    }

    @Override
    public String modelName() {
        return realProvider.modelName();
    }

    @Override
    public AiAnswer complete(AiPrompt prompt) {
        if (!realProvider.isAvailable()) {
            throw new AiUnavailableException("La IA generativa no está configurada en el servidor (GEMINI_API_KEY, GROQ_API_KEY o ANTHROPIC_API_KEY).");
        }
        String key = cacheKey(prompt);
        AiAnswer cached = readCache(key);
        if (cached != null) {
            recorder.save(AiUsage.success(prompt.userId(), prompt.purpose(), cached, 0, BigDecimal.ZERO));
            return cached;
        }
        int quota = quotaFor(prompt.userId());
        if (usedToday(prompt.userId()) >= quota) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                "Alcanzaste el límite diario de " + quota + " consultas de IA de tu plan. Los análisis estadísticos siguen disponibles.");
        }
        long start = System.currentTimeMillis();
        AiAnswer answer = realProvider.complete(prompt);
        recorder.save(AiUsage.success(prompt.userId(), prompt.purpose(), answer, System.currentTimeMillis() - start,
            pricing.estimate(answer.model(), answer.inputTokens(), answer.outputTokens())));
        writeCache(key, answer);
        return answer;
    }

    /**
     * Verifica la cuota antes de una tarea de varios turnos (agente). La tarea completa cuenta como una consulta.
     */
    public void ensureQuota(Long userId) {
        int quota = quotaFor(userId);
        if (usedToday(userId) >= quota) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                "Alcanzaste el límite diario de " + quota + " consultas de IA de tu plan. Los análisis estadísticos siguen disponibles.");
        }
    }

    public void recordUsage(Long userId, String purpose, String provider, String model, long inputTokens, long outputTokens,
                            long latencyMs) {
        recorder.save(AiUsage.success(userId, purpose, provider, model, inputTokens, outputTokens, latencyMs,
            pricing.estimate(model, inputTokens, outputTokens)));
    }

    public long usedToday(Long userId) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        return usageRepository.countByUserIdAndCachedFalseAndSuccessTrueAndCreatedAtAfter(userId, startOfDay);
    }

    public int quotaFor(Long userId) {
        Plan plan = userRepository.findById(userId).map(user -> user.getPlan()).orElse(Plan.FREE);
        return plan == Plan.PREMIUM ? config.dailyQuotaPremium() : config.dailyQuotaFree();
    }

    private AiAnswer readCache(String key) {
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            return entry != null && entry.expiresAt().isAfter(Instant.now()) ? entry.answer().asCached() : null;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    private void writeCache(String key, AiAnswer answer) {
        cacheLock.writeLock().lock();
        try {
            cache.values().removeIf(entry -> entry.expiresAt().isBefore(Instant.now()));
            cache.put(key, new CacheEntry(answer, Instant.now().plus(Duration.ofMinutes(config.cacheMinutes()))));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private static String cacheKey(AiPrompt prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(prompt.userId()).getBytes(StandardCharsets.UTF_8));
            digest.update(prompt.system().getBytes(StandardCharsets.UTF_8));
            digest.update(prompt.user().getBytes(StandardCharsets.UTF_8));
            digest.update(String.valueOf(prompt.responseSchema()).getBytes(StandardCharsets.UTF_8));
            digest.update(new java.util.TreeSet<>(prompt.excludedModels()).toString().getBytes(StandardCharsets.UTF_8));
            prompt.history().forEach(turn -> digest.update(((turn.assistant() ? "A:" : "U:") + turn.content())
                .getBytes(StandardCharsets.UTF_8)));
            if (prompt.hasImage()) {
                digest.update(prompt.image().data());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record CacheEntry(AiAnswer answer, Instant expiresAt) {
    }
}
