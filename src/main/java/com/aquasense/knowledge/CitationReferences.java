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

    public static Result filter(String text, Set<String> allowed) {
        Set<String> kept = new LinkedHashSet<>();
        List<String> removed = new ArrayList<>();
        Matcher m = REFERENCE.matcher(text == null ? "" : text);
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
