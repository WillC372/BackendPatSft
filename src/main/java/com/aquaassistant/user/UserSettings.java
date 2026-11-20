package com.aquaassistant.user;

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

    @Column(name = "active_place_id")
    private Long activePlaceId;

    @Column(name = "email_alerts")
    private boolean emailAlerts = true;

    @Column(name = "voice_enabled")
    private boolean voiceEnabled = true;

    /** Whether the pH alarm also goes by SMS to the account's mobile number (V30). */
    @Column(name = "sms_alerts")
    private boolean smsAlerts = true;

    protected UserSettings() {
    }

    public static UserSettings defaultsFor(Long userId, Long defaultProfileId) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        settings.activeProfileId = defaultProfileId;
        settings.theme = Theme.LIGHT;
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

    public void activatePlace(Long placeId) {
        this.activePlaceId = placeId;
    }

    public Long getUserId() { return userId; }
    public Long getActiveProfileId() { return activeProfileId; }
    public Theme getTheme() { return theme; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public boolean isAutoSave() { return autoSave; }
    public String getLanguage() { return language; }
    public Long getActivePlaceId() { return activePlaceId; }
    public boolean isEmailAlerts() { return emailAlerts; }


    public boolean isVoiceEnabled() { return voiceEnabled; }

    public boolean isSmsAlerts() { return smsAlerts; }

    public void setSmsAlerts(boolean smsAlerts) {
        this.smsAlerts = smsAlerts;
    }

    public void setVoiceEnabled(boolean voiceEnabled) {
        this.voiceEnabled = voiceEnabled;
    }

    public void setEmailAlerts(boolean emailAlerts) {
        this.emailAlerts = emailAlerts;
    }
}
