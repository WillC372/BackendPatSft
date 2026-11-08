package com.aquasense.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns text into index terms. It works the same for Spanish and English: lowercase, no accents,
 * no stop words and a cut at 6 characters that groups variants ("corrosión", "corrosivo",
 * "corrosive" → "corros"). Decimal numbers are kept ("6.5", "8,4" → "8.4").
 */
public final class TextAnalyzer {

    static final int STEM_LENGTH = 6;
    private static final Pattern TOKEN = Pattern.compile("\\d+(?:[.,]\\d+)?|\\p{L}+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Set<String> STOPWORDS = Set.of(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "a", "en", "y", "o", "u", "que", "es",
        "por", "para", "con", "sin", "se", "su", "sus", "lo", "le", "les", "mi", "mis", "tu", "como", "mas", "muy", "si",
        "no", "ya", "este", "esta", "esto", "ese", "esa", "son", "ser", "hay", "puede", "pueden", "debe", "cual", "cuando",
        "donde", "agua", "ph",
        "the", "of", "and", "or", "to", "in", "is", "be", "for", "by", "on", "at", "as", "an", "it", "its", "that", "this",
        "with", "are", "may", "from", "not", "any", "such", "which", "shall", "water");

    private TextAnalyzer() {
    }

    public static List<String> terms(String text) {
        List<String> terms = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return terms;
        }
        String folded = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
        Matcher m = TOKEN.matcher(folded);
        while (m.find()) {
            String token = m.group();
            if (Character.isDigit(token.charAt(0))) {
                terms.add(token.replace(',', '.'));
            } else if (token.length() > 1 && !STOPWORDS.contains(token)) {
                terms.add(token.length() > STEM_LENGTH ? token.substring(0, STEM_LENGTH) : token);
            }
        }
        return terms;
    }
}
