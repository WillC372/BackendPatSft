package com.poolguard.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A chemical maintenance action performed on the pool (auditable).
 */
public class MaintenanceRecord {

    private long id;
    private long poolId;
    private String performedBy;
    private String description;
    private String chemicalUsed;
    private double quantity;
    private String unit;
    private long timestamp;

    public MaintenanceRecord() {
    }

    public MaintenanceRecord(long id, long poolId, String performedBy, String description,
                             String chemicalUsed, double quantity, String unit, long timestamp) {
        this.id = id;
        this.poolId = poolId;
        this.performedBy = performedBy;
        this.description = description;
        this.chemicalUsed = chemicalUsed;
        this.quantity = quantity;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPoolId() {
        return poolId;
    }

    public void setPoolId(long poolId) {
        this.poolId = poolId;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getChemicalUsed() {
        return chemicalUsed;
    }

    public void setChemicalUsed(String chemicalUsed) {
        this.chemicalUsed = chemicalUsed;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("poolId", poolId);
        map.put("performedBy", performedBy);
        map.put("description", description);
        map.put("chemicalUsed", chemicalUsed);
        map.put("quantity", quantity);
        map.put("unit", unit);
        map.put("timestamp", timestamp);
        return map;
    }

    public static MaintenanceRecord fromMap(Map<String, Object> map) {
        MaintenanceRecord m = new MaintenanceRecord();
        m.setId(asLong(map.get("id")));
        m.setPoolId(asLong(map.get("poolId")));
        m.setPerformedBy(String.valueOf(map.getOrDefault("performedBy", "")));
        m.setDescription(String.valueOf(map.getOrDefault("description", "")));
        m.setChemicalUsed(String.valueOf(map.getOrDefault("chemicalUsed", "")));
        m.setQuantity(asDouble(map.get("quantity")));
        m.setUnit(String.valueOf(map.getOrDefault("unit", "")));
        m.setTimestamp(asLong(map.get("timestamp")));
        return m;
    }

    private static double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    @Override
    public String toString() {
        return "MaintenanceRecord{id=" + id + ", by=" + performedBy + ", desc=" + description + "}";
    }
}
