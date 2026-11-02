package com.poolguard.patterns.repository;

import com.poolguard.db.DataStore;
import com.poolguard.model.MaintenanceRecord;

import java.util.Map;

/**
 * Repository for maintenance (chemical treatment) records.
 */
public class MaintenanceRepository extends BaseRepository<MaintenanceRecord, Long> {

    public MaintenanceRepository(DataStore store) {
        super(store, "maintenance_records", MaintenanceRecord::fromMap);
    }

    @Override
    protected Map<String, Object> toMap(MaintenanceRecord entity) {
        return entity.toMap();
    }
}
