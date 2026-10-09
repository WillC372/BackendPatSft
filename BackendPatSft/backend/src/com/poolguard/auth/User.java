package com.poolguard.auth;

import java.util.HashMap;
import java.util.Map;

/**
 * Application user. The password is ALWAYS stored as a PBKDF2-SHA256 hash,
 * never in plain text.
 */
public class User {

    private long id;
    private String username;
    private String passwordHash;
    private Role role;
    private String fullName;

    public User() {
    }

    public User(long id, String username, String passwordHash, Role role, String fullName) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("username", username);
        map.put("passwordHash", passwordHash);
        map.put("role", role.name());
        map.put("fullName", fullName);
        return map;
    }

    public static User fromMap(Map<String, Object> map) {
        User u = new User();
        u.setId(asLong(map.get("id")));
        u.setUsername(String.valueOf(map.getOrDefault("username", "")));
        u.setPasswordHash(String.valueOf(map.getOrDefault("passwordHash", "")));
        u.setRole(Role.valueOf(String.valueOf(map.getOrDefault("role", "OPERATOR"))));
        u.setFullName(String.valueOf(map.getOrDefault("fullName", "")));
        return u;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
