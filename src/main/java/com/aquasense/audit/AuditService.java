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

    @Transactional
    public void record(Long userId, String action, String detail) {
        repository.save(new AuditEntry(userId, action, detail));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(Long userId, String action, String detail) {
        repository.save(new AuditEntry(userId, action, detail));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> latest(Long userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }
}
