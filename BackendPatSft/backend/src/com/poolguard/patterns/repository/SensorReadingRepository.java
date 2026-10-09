package com.poolguard.patterns.repository;

import com.poolguard.db.DataStore;
import com.poolguard.model.SensorReading;

import java.util.Map;

/**
 * Repository for the sensor time-series.
 */
public class SensorReadingRepository extends BaseRepository<SensorReading, Long> {

    public SensorReadingRepository(DataStore store) {
        super(store, "sensor_readings", SensorReading::fromMap);
    }

    /** Returns the most recently recorded reading, or null if none exists. */
    public SensorReading findLatest() {
        java.util.List<SensorReading> all = findAll();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    @Override
    protected Map<String, Object> toMap(SensorReading entity) {
        return entity.toMap();
    }
}
