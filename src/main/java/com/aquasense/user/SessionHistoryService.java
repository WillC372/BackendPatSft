package com.aquasense.user;

import com.aquasense.audit.ActionLabels;
import com.aquasense.audit.AuditEntry;
import com.aquasense.audit.AuditRepository;
import com.aquasense.common.BusinessException;
import com.aquasense.user.dto.SessionDtos;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * History of uses of the application. Each login opens a {@link UserSession}; every action the user does with that
 * token is stored with the session (see {@code AuditService}), and logout closes it. Logging in again opens a new one.
 */
@Service
public class SessionHistoryService {

    /** The last activity is written at most once per minute per session, not on every request. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);
    private static final int MAX_PAGE_SIZE = 50;

    private final UserSessionRepository sessions;
    private final AuditRepository audit;
    /** When each session last wrote its activity; sessions not seen for 10 minutes leave the map by themselves. */
    private final com.aquasense.common.ExpiringCache<Long, Instant> lastTouch =
        new com.aquasense.common.ExpiringCache<>(Duration.ofMinutes(10), 50_000);

    public SessionHistoryService(UserSessionRepository sessions, AuditRepository audit) {
        this.sessions = sessions;
        this.audit = audit;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void forgetIdleSessions() {
        lastTouch.evictExpired();
    }

    @Transactional
    public UserSession start(Long userId, Instant now, Instant expiresAt, boolean rememberMe, String userAgent) {
        return sessions.save(new UserSession(userId, now, expiresAt, rememberMe, clientLabel(userAgent)));
    }

    /** {@code true} if the session exists, belongs to the user and is open; it also records the activity. */
    @Transactional
    public boolean touch(Long sessionId, Long userId) {
        Instant now = Instant.now();
        return sessions.findById(sessionId).filter(s -> s.getUserId().equals(userId) && s.isOpen(now)).map(s -> {
            Instant previous = lastTouch.getIfPresent(sessionId);
            if (previous == null || Duration.between(previous, now).compareTo(TOUCH_INTERVAL) >= 0) {
                s.touch(now);
                lastTouch.put(sessionId, now);
            }
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<UserSession> find(Long sessionId, Long userId) {
        return sessions.findByIdAndUserId(sessionId, userId);
    }

    @Transactional
    public void end(Long sessionId, Long userId, UserSession.EndReason reason) {
        sessions.findByIdAndUserId(sessionId, userId).ifPresent(s -> s.end(reason, Instant.now()));
        lastTouch.remove(sessionId);
    }

    /** Ends every open session of the user, except {@code keep} (may be {@code null}). Returns how many were closed. */
    @Transactional
    public int endAll(Long userId, UserSession.EndReason reason, Long keep) {
        Instant now = Instant.now();
        int closed = 0;
        for (UserSession s : sessions.findByUserIdAndEndedAtIsNull(userId)) {
            if (!s.getId().equals(keep) && s.isOpen(now)) {
                s.end(reason, now);
                lastTouch.remove(s.getId());
                closed++;
            }
        }
        return closed;
    }

    @Transactional(readOnly = true)
    public SessionDtos.SessionPage list(Long userId, Long currentSessionId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<UserSession> result = sessions.findByUserIdOrderByStartedAtDesc(userId, PageRequest.of(Math.max(page, 0), safeSize));
        List<Long> ids = result.getContent().stream().map(UserSession::getId).toList();
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : audit.countBySession(ids)) {
                counts.computeIfAbsent((Long) row[0], k -> new HashMap<>()).put((String) row[1], (Long) row[2]);
            }
        }
        Instant now = Instant.now();
        List<SessionDtos.SessionSummary> items = result.getContent().stream()
            .map(s -> summary(s, counts.getOrDefault(s.getId(), Map.of()), currentSessionId, now)).toList();
        return new SessionDtos.SessionPage(items, result.getTotalElements(), result.getNumber(), safeSize, result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SessionDtos.SessionDetail detail(Long userId, Long sessionId, Long currentSessionId) {
        UserSession s = sessions.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> BusinessException.notFound("No se encontró esa sesión."));
        List<AuditEntry> entries = audit.findBySessionIdOrderByCreatedAtAsc(sessionId);
        Map<String, Long> counts = new HashMap<>();
        entries.forEach(e -> counts.merge(e.getAction(), 1L, Long::sum));
        List<SessionDtos.SessionAction> actions = entries.stream()
            .map(e -> new SessionDtos.SessionAction(e.getCreatedAt(), e.getAction(), ActionLabels.of(e.getAction()), e.getDetail()))
            .toList();
        return new SessionDtos.SessionDetail(summary(s, counts, currentSessionId, Instant.now()), actions);
    }

    private static SessionDtos.SessionSummary summary(UserSession s, Map<String, Long> counts, Long currentSessionId,
                                                      Instant now) {
        String status;
        String statusLabel;
        Instant end;
        if (s.getEndedAt() != null) {
            status = "ENDED";
            statusLabel = s.getEndReason() == null ? "Terminada" : s.getEndReason().label();
            end = s.getEndedAt();
        } else if (!now.isBefore(s.getExpiresAt())) {
            status = "EXPIRED";
            statusLabel = "Venció sin cerrar sesión";
            end = s.getLastActivityAt();
        } else {
            status = "OPEN";
            statusLabel = "Abierta";
            end = s.getLastActivityAt();
        }
        long minutes = Math.max(0, Duration.between(s.getStartedAt(), end).toMinutes());
        // Login and logout are the edges of the session; what is counted is what the user did in it.
        Map<String, Long> done = new LinkedHashMap<>();
        counts.entrySet().stream()
            .filter(e -> !e.getKey().equals("USER_LOGIN") && !e.getKey().equals("USER_LOGOUT") && !e.getKey().equals("USER_REGISTERED"))
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> done.put(ActionLabels.of(e.getKey()), e.getValue()));
        long total = done.values().stream().mapToLong(Long::longValue).sum();
        return new SessionDtos.SessionSummary(s.getId(), s.getStartedAt(), s.getLastActivityAt(), s.getEndedAt(),
            s.getExpiresAt(), status, statusLabel, s.getClient(), s.isRememberMe(), s.getId().equals(currentSessionId),
            minutes, total, done);
    }

    /** Short description of the browser, e.g. "Chrome en Windows". The full User-Agent and the IP are not stored. */
    static String clientLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String browser = ua.contains("edg/") ? "Edge" : ua.contains("opr/") || ua.contains("opera") ? "Opera"
            : ua.contains("firefox/") ? "Firefox" : ua.contains("samsungbrowser") ? "Samsung Internet"
            : ua.contains("chrome/") || ua.contains("crios/") ? "Chrome" : ua.contains("safari/") ? "Safari"
            : ua.contains("curl/") ? "curl" : "Otro navegador";
        String system = ua.contains("android") ? "Android" : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
            : ua.contains("windows") ? "Windows" : ua.contains("mac os") ? "macOS" : ua.contains("cros") ? "ChromeOS"
            : ua.contains("linux") ? "Linux" : null;
        return system == null ? browser : browser + " en " + system;
    }
}
