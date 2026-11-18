package com.aquaassistant.i18n;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Languages the interface can be shown in. Spanish is the original text; the others are translated with the AI the
 * first time someone uses them. The list covers the languages with the most speakers and those the models translate
 * well; right-to-left languages are marked so the page is mirrored.
 */
public final class Languages {

    public static final String ORIGINAL = "es";

    private static final List<String> CODES = List.of(
        "es", "en", "pt-BR", "pt-PT", "fr", "it", "de", "nl", "ca", "gl", "eu", "qu", "gn",
        "zh-Hans", "zh-Hant", "ja", "ko", "hi", "bn", "ur", "pa", "mr", "te", "ta", "gu", "kn", "ml",
        "ar", "he", "fa", "tr", "ru", "uk", "pl", "cs", "sk", "ro", "hu", "el", "bg", "sr", "hr", "sl",
        "sv", "no", "da", "fi", "et", "lv", "lt", "id", "ms", "vi", "th", "tl", "sw", "am", "yo", "ha", "zu", "af");

    private static final Set<String> RIGHT_TO_LEFT = Set.of("ar", "he", "fa", "ur");

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

    /** Scripts the PDF engine draws with its font (Latin, Greek, Cyrillic) without needing text shaping. */
    private static final Set<String> NOT_IN_PDF = Set.of("zh-Hans", "zh-Hant", "ja", "ko", "hi", "bn", "ur", "pa", "mr", "te",
        "ta", "gu", "kn", "ml", "ar", "he", "fa", "th", "am");

    public static boolean drawableInPdf(String code) {
        return !NOT_IN_PDF.contains(code);
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
            case "zh-Hans" -> "简体中文";
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
