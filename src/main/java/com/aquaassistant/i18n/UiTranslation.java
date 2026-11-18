package com.aquaassistant.i18n;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A Spanish text of the interface and its translation into one language. The id is the language plus a hash. */
@Entity
@Table(name = "ui_translations")
public class UiTranslation {

    @Id
    private String id;

    @Column(nullable = false)
    private String lang;

    @Column(name = "source_text", nullable = false)
    private String sourceText;

    @Column(name = "target_text", nullable = false)
    private String targetText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UiTranslation() {
    }

    public UiTranslation(String id, String lang, String sourceText, String targetText) {
        this.id = id;
        this.lang = lang;
        this.sourceText = sourceText;
        this.targetText = targetText;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getLang() { return lang; }
    public String getSourceText() { return sourceText; }
    public String getTargetText() { return targetText; }
}
