package com.aquasense.user.dto;

import com.aquasense.user.Theme;
import com.aquasense.user.UserSettings;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SettingsDto(
    Long activeProfileId,
    @NotNull(message = "Elige un tema.") Theme theme,
    boolean notificationsEnabled,
    boolean autoSave,
    @Pattern(regexp = "es|en", message = "Idioma no soportado.") String language) {

    public static SettingsDto from(UserSettings settings) {
        return new SettingsDto(settings.getActiveProfileId(), settings.getTheme(), settings.isNotificationsEnabled(),
            settings.isAutoSave(), settings.getLanguage());
    }
}
