package com.aquasense.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contracts of the session history. */
public final class SessionDtos {

    private SessionDtos() {
    }

    /**
     * One session. {@code status}: OPEN, ENDED or EXPIRED (the token expired without logging out). {@code actions}:
     * how many times each thing was done in it, by readable name.
     */
    public record SessionSummary(Long id, Instant startedAt, Instant lastActivityAt, Instant endedAt, Instant expiresAt,
                                 String status, String statusLabel, String client, boolean rememberMe, boolean current,
                                 long durationMinutes, long actionCount, Map<String, Long> actions) {
    }

    public record SessionPage(List<SessionSummary> items, long total, int page, int size, int totalPages) {
    }

    public record SessionAction(Instant at, String action, String label, String detail) {
    }

    public record SessionDetail(SessionSummary session, List<SessionAction> actions) {
    }
}
