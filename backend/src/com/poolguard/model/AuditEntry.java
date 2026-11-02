package com.poolguard.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable audit trail entry produced by the Command pattern.
 */
public class AuditEntry {

    private long id;
    private String username;
    private String action;
    private String details;
    private long timestamp;

    public AuditEntry() {
    }

    public AuditEntry(long id, String username, String action, String details, long timestamp) {
        this.id = id;
        this.username = username;
        this.action = action;
        this.details = details;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
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
        map.put("username", username);
        map.put("action", action);
        map.put("details", details);
        map.put("timestamp", timestamp);
        return map;
    }

    public static AuditEntry fromMap(Map<String, Object> map) {
        AuditEntry e = new AuditEntry();
        e.setId(asLong(map.get("id")));
        e.setUsername(String.valueOf(map.getOrDefault("username", "")));
        e.setAction(String.valueOf(map.getOrDefault("action", "")));
        e.setDetails(String.valueOf(map.getOrDefault("details", "")));
        e.setTimestamp(asLong(map.get("timestamp")));
        return e;
    }

    private static double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
