package com.poolguard.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A swimming pool monitored by the system.
 */
public class Pool {

    private long id;
    private String name;
    private double volumeM3;

    public Pool() {
    }

    public Pool(long id, String name, double volumeM3) {
        this.id = id;
        this.name = name;
        this.volumeM3 = volumeM3;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getVolumeM3() {
        return volumeM3;
    }

    public void setVolumeM3(double volumeM3) {
        this.volumeM3 = volumeM3;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("volumeM3", volumeM3);
        return map;
    }

    public static Pool fromMap(Map<String, Object> map) {
        Pool p = new Pool();
        p.setId(asLong(map.get("id")));
        p.setName(String.valueOf(map.getOrDefault("name", "Piscina")));
        p.setVolumeM3(asDouble(map.get("volumeM3")));
        return p;
    }

    private static double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    @Override
    public String toString() {
        return "Pool{id=" + id + ", name=" + name + ", volumeM3=" + volumeM3 + "}";
    }
}
