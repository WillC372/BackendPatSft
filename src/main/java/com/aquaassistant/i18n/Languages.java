package com.aquaassistant.i18n;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Languages the interface can be shown in: Spanish (the original text), English, French, German, Italian, Simplified
 * Chinese and Japanese. Each text is translated once with the AI and stored; the server keeps them translated in
 * advance (UiTextWarmer), so changing the language is immediate.
 */
public final class Languages {

    public static final String ORIGINAL = "es";

    private static final List<String> CODES = List.of("es", "en", "fr", "de", "it", "zh-Hans", "ja");

    private static final Set<String> RIGHT_TO_LEFT = Set.of();

    private static final Map<String, String> BY_LOWER = new LinkedHashMap<>();

    static {
        CODES.forEach(code -> BY_LOWER.put(code.toLowerCase(Locale.ROOT), code));
    }

    private Languages() {
    }

    public static List<String> codes() {
        return CODES;
    }

    /** The code as listed ("pt-br" becomes "pt-BR"), or null when it is not supported. */
    public static String normalize(String code) {
        return code == null ? null : BY_LOWER.get(code.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isOriginal(String code) {
        String normalized = normalize(code);
        return normalized == null || ORIGINAL.equals(normalized);
    }

    /** Every language is drawn in the PDF: DejaVu for the Latin alphabet and a CJK font for Chinese and Japanese. */
    public static boolean drawableInPdf(String code) {
        return normalize(code) != null;
    }

    public static boolean rightToLeft(String code) {
        return RIGHT_TO_LEFT.contains(code);
    }

    /** Name in English, for the instructions to the AI ("Portuguese (Brazil)"). */
    public static String englishName(String code) {
        return switch (code) {
            case "zh-Hans" -> "Simplified Chinese";
            case "zh-Hant" -> "Traditional Chinese";
            case "qu" -> "Quechua";
            case "gn" -> "Guarani";
            default -> Locale.forLanguageTag(code).getDisplayName(Locale.ENGLISH);
        };
    }

    /** Name in the language itself ("Português (Brasil)"), for the selector. */
    public static String nativeName(String code) {
        String name = switch (code) {
            case "zh-Hans" -> "中文（简体）";
            case "zh-Hant" -> "繁體中文";
            case "qu" -> "Runa Simi";
            case "gn" -> "Avañe'ẽ";
            case "eu" -> "Euskara";
            default -> {
                Locale locale = Locale.forLanguageTag(code);
                yield locale.getDisplayName(locale);
            }
        };
        return name.isEmpty() ? code : name.substring(0, 1).toUpperCase(Locale.forLanguageTag(code)) + name.substring(1);
    }
}
