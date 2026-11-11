package com.aquasense.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Free-text references with the format {@code [fragment-id]}. The chat AI cites this way; the system keeps
 * only the ids it provided in the context and removes any other, so an invented citation never reaches the screen.
 */
public final class CitationReferences {

    private static final Pattern REFERENCE = Pattern.compile("\\[([a-z0-9]+(?:-[a-z0-9]+)+)]");

    private CitationReferences() {
    }

    /** "(id)" or "[id1, id2]": other ways the model sometimes writes a reference. */
    private static final Pattern LOOSE = Pattern.compile("[\\[(]\\s*([a-z0-9]+(?:-[a-z0-9]+)+(?:\\s*[,;]\\s*[a-z0-9]+(?:-[a-z0-9]+)+)*)\\s*[\\])]");

    /**
     * Rewrites references written in parentheses or grouped in one pair of brackets as {@code [id]} each, but only when
     * every id in them was delivered; any other parenthesis is left as the model wrote it.
     */
    static String normalize(String text, Set<String> allowed) {
        Matcher m = LOOSE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String[] ids = m.group(1).split("\\s*[,;]\\s*");
            boolean all = java.util.Arrays.stream(ids).allMatch(allowed::contains);
            String replacement = all ? java.util.Arrays.stream(ids).map(id -> "[" + id + "]").collect(java.util.stream.Collectors.joining())
                : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    public static Result filter(String text, Set<String> allowed) {
        Set<String> kept = new LinkedHashSet<>();
        List<String> removed = new ArrayList<>();
        Matcher m = REFERENCE.matcher(normalize(text == null ? "" : text, allowed));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String id = m.group(1);
            if (allowed.contains(id)) {
                kept.add(id);
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            } else {
                removed.add(id);
                m.appendReplacement(out, "");
            }
        }
        m.appendTail(out);
        String cleaned = out.toString().replaceAll(" +([.,;:])", "$1").replaceAll(" {2,}", " ").trim();
        return new Result(cleaned, List.copyOf(kept), List.copyOf(removed));
    }

    public record Result(String text, List<String> citedIds, List<String> removedIds) {
    }
}
