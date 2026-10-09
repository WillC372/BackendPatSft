package com.poolguard.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A persisted alert (PUSH / VOICE / VISUAL) with its Spanish message.
 */
public class AlertRecord {

    private long id;
    private String type;      // PUSH | VOICE | VISUAL
    private String severity;  // INFO | WARNING | CRITICAL
    private String messageEs;
    private long timestamp;

    public AlertRecord() {
    }

    public AlertRecord(long id, String type, String severity, String messageEs, long timestamp) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.messageEs = messageEs;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessageEs() {
        return messageEs;
    }

    public void setMessageEs(String messageEs) {
        this.messageEs = messageEs;
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
        map.put("type", type);
        map.put("severity", severity);
        map.put("messageEs", messageEs);
        map.put("timestamp", timestamp);
        return map;
    }

    public static AlertRecord fromMap(Map<String, Object> map) {
        AlertRecord a = new AlertRecord();
        a.setId(asLong(map.get("id")));
        a.setType(String.valueOf(map.getOrDefault("type", "PUSH")));
        a.setSeverity(String.valueOf(map.getOrDefault("severity", "INFO")));
        a.setMessageEs(String.valueOf(map.getOrDefault("messageEs", "")));
        a.setTimestamp(asLong(map.get("timestamp")));
        return a;
    }

    private static double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
