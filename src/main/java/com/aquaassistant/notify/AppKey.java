package com.aquaassistant.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A key the server creates for itself once (for example the Web Push key pair) and keeps in the database. */
@Entity
@Table(name = "app_keys")
public class AppKey {

    @Id
    @Column(name = "key_name")
    private String name;

    @Column(name = "key_value")
    private String value;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AppKey() {
    }

    public AppKey(String name, String value) {
        this.name = name;
        this.value = value;
        this.createdAt = Instant.now();
    }

    public String getName() { return name; }
    public String getValue() { return value; }
}
