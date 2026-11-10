package com.aquasense.audit;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /** Records an action; if the user did it themselves in a session, the entry is added to that session's history. */
    @Transactional
    public void record(Long userId, String action, String detail) {
        repository.save(new AuditEntry(userId, currentSession(userId), action, detail));
    }

    /** Records an action of a session that is just starting (login, registration). */
    @Transactional
    public void recordInSession(Long userId, Long sessionId, String action, String detail) {
        repository.save(new AuditEntry(userId, sessionId, action, detail));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(Long userId, String action, String detail) {
        repository.save(new AuditEntry(userId, currentSession(userId), action, detail));
    }

    /** Session of the request, only when the entry belongs to the user who is making it. */
    private static Long currentSession(Long userId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.aquasense.security.AuthenticatedUser user
                && user.id().equals(userId)) {
            return user.sessionId();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> latest(Long userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }
}
