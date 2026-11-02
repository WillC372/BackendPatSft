package com.poolguard.patterns.repository;

import com.poolguard.db.DataStore;
import com.poolguard.model.AuditEntry;

import java.util.Map;

/**
 * Repository for the immutable audit log.
 */
public class AuditRepository extends BaseRepository<AuditEntry, Long> {

    public AuditRepository(DataStore store) {
        super(store, "audit_log", AuditEntry::fromMap);
    }

    @Override
    protected Map<String, Object> toMap(AuditEntry entity) {
        return entity.toMap();
    }
}
