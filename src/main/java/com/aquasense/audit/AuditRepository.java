package com.aquasense.audit;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AuditEntry> findByActionStartingWithOrderByCreatedAtDesc(String prefix, Pageable pageable);

    List<AuditEntry> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /** Number of actions of each type per session: rows {sessionId, action, count}. */
    @org.springframework.data.jpa.repository.Query("select e.sessionId, e.action, count(e) from AuditEntry e"
        + " where e.sessionId in :ids group by e.sessionId, e.action")
    List<Object[]> countBySession(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
