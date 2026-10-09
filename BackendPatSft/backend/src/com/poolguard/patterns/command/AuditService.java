package com.poolguard.patterns.command;

import com.poolguard.model.AuditEntry;
import com.poolguard.patterns.repository.AuditRepository;
import com.poolguard.patterns.repository.UnitOfWork;

/**
 * Immutable audit trail: every relevant user action is recorded in the
 * database with its author and timestamp. Entries are never modified
 * or deleted.
 */
public class AuditService {

    private final AuditRepository auditRepository;
    private final UnitOfWork unitOfWork;

    public AuditService(AuditRepository auditRepository, UnitOfWork unitOfWork) {
        this.auditRepository = auditRepository;
        this.unitOfWork = unitOfWork;
    }

    /** Appends an immutable entry to the audit log. */
    public void record(String username, String action, String details) {
        AuditEntry entry = new AuditEntry(0, username, action, details, System.currentTimeMillis());
        auditRepository.save(entry);
        unitOfWork.commit();
    }
}
