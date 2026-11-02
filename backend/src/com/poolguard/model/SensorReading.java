package com.poolguard.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A single measurement produced by the Bluetooth water meter.
 */
public class SensorReading {

    private long id;
    private double ph;
    private double temperatureC;
    private double chlorinePpm;
    private int batherLoad;
    private long timestamp; // epoch millis

    public SensorReading() {
    }

    public SensorReading(long id, double ph, double temperatureC, double chlorinePpm, int batherLoad, long timestamp) {
        this.id = id;
        this.ph = ph;
        this.temperatureC = temperatureC;
        this.chlorinePpm = chlorinePpm;
        this.batherLoad = batherLoad;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getPh() {
        return ph;
    }

    public void setPh(double ph) {
        this.ph = ph;
    }

    public double getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public double getChlorinePpm() {
        return chlorinePpm;
    }

    public void setChlorinePpm(double chlorinePpm) {
        this.chlorinePpm = chlorinePpm;
    }

    public int getBatherLoad() {
        return batherLoad;
    }

    public void setBatherLoad(int batherLoad) {
        this.batherLoad = batherLoad;
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
        map.put("ph", ph);
        map.put("temperatureC", temperatureC);
        map.put("chlorinePpm", chlorinePpm);
        map.put("batherLoad", batherLoad);
        map.put("timestamp", timestamp);
        return map;
    }

    public static SensorReading fromMap(Map<String, Object> map) {
        return new SensorReading(
                asLong(map.get("id")),
                asDouble(map.get("ph")),
                asDouble(map.get("temperatureC")),
                asDouble(map.get("chlorinePpm")),
                (int) asDouble(map.get("batherLoad")),
                asLong(map.get("timestamp")));
    }

    protected static double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    protected static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    @Override
    public String toString() {
        return "SensorReading{id=" + id + ", ph=" + ph + ", temp=" + temperatureC
                + ", chlorine=" + chlorinePpm + ", bathers=" + batherLoad + "}";
    }
}
