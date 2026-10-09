package com.poolguard.patterns.repository;

import com.poolguard.db.DataStore;
import com.poolguard.model.AlertRecord;

import java.util.Map;

/**
 * Repository for persisted alerts.
 */
public class AlertRepository extends BaseRepository<AlertRecord, Long> {

    public AlertRepository(DataStore store) {
        super(store, "alerts", AlertRecord::fromMap);
    }

    @Override
    protected Map<String, Object> toMap(AlertRecord entity) {
        return entity.toMap();
    }
}
