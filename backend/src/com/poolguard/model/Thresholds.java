package com.poolguard.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Safety thresholds that drive the State pattern classification.
 * Three bands per parameter: optimal, caution and critical.
 */
public class Thresholds {

    private double phOptimalMin = 7.2;
    private double phOptimalMax = 7.6;
    private double phCautionMin = 7.0;
    private double phCautionMax = 7.8;
    private double phCriticalMin = 6.8;
    private double phCriticalMax = 8.0;

    private double chlorineOptimalMin = 1.0;
    private double chlorineOptimalMax = 3.0;
    private double chlorineCautionMin = 0.7;
    private double chlorineCautionMax = 3.5;
    private double chlorineCriticalMin = 0.3;
    private double chlorineCriticalMax = 5.0;

    public Thresholds() {
    }

    /** Copies every value from another Thresholds instance. */
    public void apply(Thresholds other) {
        this.phOptimalMin = other.phOptimalMin;
        this.phOptimalMax = other.phOptimalMax;
        this.phCautionMin = other.phCautionMin;
        this.phCautionMax = other.phCautionMax;
        this.phCriticalMin = other.phCriticalMin;
        this.phCriticalMax = other.phCriticalMax;
        this.chlorineOptimalMin = other.chlorineOptimalMin;
        this.chlorineOptimalMax = other.chlorineOptimalMax;
        this.chlorineCautionMin = other.chlorineCautionMin;
        this.chlorineCautionMax = other.chlorineCautionMax;
        this.chlorineCriticalMin = other.chlorineCriticalMin;
        this.chlorineCriticalMax = other.chlorineCriticalMax;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("phOptimalMin", phOptimalMin);
        map.put("phOptimalMax", phOptimalMax);
        map.put("phCautionMin", phCautionMin);
        map.put("phCautionMax", phCautionMax);
        map.put("phCriticalMin", phCriticalMin);
        map.put("phCriticalMax", phCriticalMax);
        map.put("chlorineOptimalMin", chlorineOptimalMin);
        map.put("chlorineOptimalMax", chlorineOptimalMax);
        map.put("chlorineCautionMin", chlorineCautionMin);
        map.put("chlorineCautionMax", chlorineCautionMax);
        map.put("chlorineCriticalMin", chlorineCriticalMin);
        map.put("chlorineCriticalMax", chlorineCriticalMax);
        return map;
    }

    public static Thresholds fromMap(Map<String, Object> map) {
        Thresholds t = new Thresholds();
        t.phOptimalMin = asDouble(map.get("phOptimalMin"), t.phOptimalMin);
        t.phOptimalMax = asDouble(map.get("phOptimalMax"), t.phOptimalMax);
        t.phCautionMin = asDouble(map.get("phCautionMin"), t.phCautionMin);
        t.phCautionMax = asDouble(map.get("phCautionMax"), t.phCautionMax);
        t.phCriticalMin = asDouble(map.get("phCriticalMin"), t.phCriticalMin);
        t.phCriticalMax = asDouble(map.get("phCriticalMax"), t.phCriticalMax);
        t.chlorineOptimalMin = asDouble(map.get("chlorineOptimalMin"), t.chlorineOptimalMin);
        t.chlorineOptimalMax = asDouble(map.get("chlorineOptimalMax"), t.chlorineOptimalMax);
        t.chlorineCautionMin = asDouble(map.get("chlorineCautionMin"), t.chlorineCautionMin);
        t.chlorineCautionMax = asDouble(map.get("chlorineCautionMax"), t.chlorineCautionMax);
        t.chlorineCriticalMin = asDouble(map.get("chlorineCriticalMin"), t.chlorineCriticalMin);
        t.chlorineCriticalMax = asDouble(map.get("chlorineCriticalMax"), t.chlorineCriticalMax);
        return t;
    }

    private static double asDouble(Object value, double def) {
        return value instanceof Number ? ((Number) value).doubleValue() : def;
    }

    // ----- Getters and setters -----

    public double getPhOptimalMin() {
        return phOptimalMin;
    }

    public void setPhOptimalMin(double phOptimalMin) {
        this.phOptimalMin = phOptimalMin;
    }

    public double getPhOptimalMax() {
        return phOptimalMax;
    }

    public void setPhOptimalMax(double phOptimalMax) {
        this.phOptimalMax = phOptimalMax;
    }

    public double getPhCautionMin() {
        return phCautionMin;
    }

    public void setPhCautionMin(double phCautionMin) {
        this.phCautionMin = phCautionMin;
    }

    public double getPhCautionMax() {
        return phCautionMax;
    }

    public void setPhCautionMax(double phCautionMax) {
        this.phCautionMax = phCautionMax;
    }

    public double getPhCriticalMin() {
        return phCriticalMin;
    }

    public void setPhCriticalMin(double phCriticalMin) {
        this.phCriticalMin = phCriticalMin;
    }

    public double getPhCriticalMax() {
        return phCriticalMax;
    }

    public void setPhCriticalMax(double phCriticalMax) {
        this.phCriticalMax = phCriticalMax;
    }

    public double getChlorineOptimalMin() {
        return chlorineOptimalMin;
    }

    public void setChlorineOptimalMin(double chlorineOptimalMin) {
        this.chlorineOptimalMin = chlorineOptimalMin;
    }

    public double getChlorineOptimalMax() {
        return chlorineOptimalMax;
    }

    public void setChlorineOptimalMax(double chlorineOptimalMax) {
        this.chlorineOptimalMax = chlorineOptimalMax;
    }

    public double getChlorineCautionMin() {
        return chlorineCautionMin;
    }

    public void setChlorineCautionMin(double chlorineCautionMin) {
        this.chlorineCautionMin = chlorineCautionMin;
    }

    public double getChlorineCautionMax() {
        return chlorineCautionMax;
    }

    public void setChlorineCautionMax(double chlorineCautionMax) {
        this.chlorineCautionMax = chlorineCautionMax;
    }

    public double getChlorineCriticalMin() {
        return chlorineCriticalMin;
    }

    public void setChlorineCriticalMin(double chlorineCriticalMin) {
        this.chlorineCriticalMin = chlorineCriticalMin;
    }

    public double getChlorineCriticalMax() {
        return chlorineCriticalMax;
    }

    public void setChlorineCriticalMax(double chlorineCriticalMax) {
        this.chlorineCriticalMax = chlorineCriticalMax;
    }
}
