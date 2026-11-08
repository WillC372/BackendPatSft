package com.aquasense.intelligence.analysis;

import com.aquasense.intelligence.agent.Verification;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verificación de cifras: cada número decimal entre 0 y 14 de un texto generado por IA debe coincidir, con el redondeo
 * usado, con algún valor que el sistema le entregó. Los que no coinciden se devuelven para mostrarlos como no verificados.
 */
public final class FigureCheck {

    private static final Pattern DECIMAL = Pattern.compile("(?<![\\d.,])(\\d{1,2})[.,](\\d{1,3})(?![\\d])");

    private FigureCheck() {
    }

    public static Verification check(String text, Set<Double> facts) {
        Matcher matcher = DECIMAL.matcher(text == null ? "" : text);
        int checked = 0;
        int verified = 0;
        Set<String> unverified = new LinkedHashSet<>();
        while (matcher.find()) {
            String decimals = matcher.group(2);
            double value = Double.parseDouble(matcher.group(1) + "." + decimals);
            if (value > 14) {
                continue;
            }
            checked++;
            double tolerance = 0.5 * Math.pow(10, -decimals.length()) + 1e-9;
            if (facts.stream().anyMatch(f -> Math.abs(f - value) <= tolerance)) {
                verified++;
            } else {
                unverified.add(String.format(Locale.US, "%s", matcher.group(0)));
            }
        }
        return new Verification(checked, verified, List.copyOf(unverified));
    }
}
