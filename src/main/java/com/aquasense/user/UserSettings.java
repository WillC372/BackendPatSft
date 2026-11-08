package com.aquasense.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "active_profile_id")
    private Long activeProfileId;

    @Enumerated(EnumType.STRING)
    private Theme theme;

    @Column(name = "notifications_enabled")
    private boolean notificationsEnabled;

    @Column(name = "auto_save")
    private boolean autoSave;

    private String language;

    protected UserSettings() {
    }

    public static UserSettings defaultsFor(Long userId, Long defaultProfileId) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        settings.activeProfileId = defaultProfileId;
        settings.theme = Theme.DARK;
        settings.notificationsEnabled = true;
        settings.autoSave = false;
        settings.language = "es";
        return settings;
    }

    public void update(Theme theme, boolean notificationsEnabled, boolean autoSave, String language) {
        this.theme = theme;
        this.notificationsEnabled = notificationsEnabled;
        this.autoSave = autoSave;
        this.language = language;
    }

    public void activateProfile(Long profileId) {
        this.activeProfileId = profileId;
    }

    public Long getUserId() { return userId; }
    public Long getActiveProfileId() { return activeProfileId; }
    public Theme getTheme() { return theme; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public boolean isAutoSave() { return autoSave; }
    public String getLanguage() { return language; }
}
