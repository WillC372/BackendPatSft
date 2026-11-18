package com.aquaassistant.user.dto;

import com.aquaassistant.user.Theme;
import com.aquaassistant.user.UserSettings;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SettingsDto(
    Long activeProfileId,
    @NotNull(message = "Elige un tema.") Theme theme,
    boolean notificationsEnabled,
    boolean autoSave,
    @Pattern(regexp = "[a-z]{2,3}(-[A-Za-z0-9]{2,4})?", message = "Idioma no soportado.") String language,
    Boolean emailAlerts,
    Boolean voiceEnabled) {

    public SettingsDto(Long activeProfileId, Theme theme, boolean notificationsEnabled, boolean autoSave, String language) {
        this(activeProfileId, theme, notificationsEnabled, autoSave, language, null, null);
    }

    public SettingsDto(Long activeProfileId, Theme theme, boolean notificationsEnabled, boolean autoSave, String language,
                       Boolean emailAlerts) {
        this(activeProfileId, theme, notificationsEnabled, autoSave, language, emailAlerts, null);
    }

    public static SettingsDto from(UserSettings settings) {
        return new SettingsDto(settings.getActiveProfileId(), settings.getTheme(), settings.isNotificationsEnabled(),
            settings.isAutoSave(), settings.getLanguage(), settings.isEmailAlerts(), settings.isVoiceEnabled());
    }
}
