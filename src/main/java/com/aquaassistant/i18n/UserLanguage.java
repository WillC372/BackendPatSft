package com.aquaassistant.i18n;

import com.aquaassistant.user.UserSettingsRepository;
import org.springframework.stereotype.Component;

/** The language each person chose in Configuración; Spanish when they did not choose another. */
@Component
public class UserLanguage {

    private final UserSettingsRepository settings;

    public UserLanguage(UserSettingsRepository settings) {
        this.settings = settings;
    }

    public String of(Long userId) {
        if (userId == null) {
            return Languages.ORIGINAL;
        }
        String code = settings.findById(userId).map(s -> Languages.normalize(s.getLanguage())).orElse(null);
        return code == null ? Languages.ORIGINAL : code;
    }

    /**
     * Added at the end of the instructions to the AI so it writes for the person in their language, although the
     * instructions and the data are in Spanish. Empty for Spanish.
     */
    public String instruction(Long userId) {
        String code = of(userId);
        if (Languages.isOriginal(code)) {
            return "";
        }
        String name = Languages.englishName(code);
        return "\n\nLANGUAGE: this person uses the app in " + name + ". Write every text meant for them (answers, titles, "
            + "summaries, explanations, recommendations and the text values inside JSON) in " + name + ", even though these "
            + "instructions, the data and the guideline excerpts are in Spanish or English. Keep JSON keys, enum values, "
            + "numbers, units and source identifiers exactly as specified. If an earlier instruction says to write in "
            + "Spanish, write in " + name + " instead.";
    }
}
