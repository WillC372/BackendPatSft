package com.aquasense.device.hardware;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the pH and the temperature from the text line a laboratory meter sends over USB or RS-232, for example
 * {@code "pH 7.02  25.0 °C"}, {@code "7.02 pH"}, {@code "PH=6.98,T=24.8C"} or {@code "07.02"}. A number is taken as pH
 * when it is labelled as pH, or when it is the only number of the line and lies between 0 and 14.
 * The browser uses the same rules (frontend {@code MeterProtocols.js}).
 */
public final class TextMeterParser {

    private static final String NUMBER = "([-+]?\\d{1,2}(?:[.,]\\d{1,3})?)";
    private static final Pattern PH_BEFORE = Pattern.compile("p\\s*h\\s*[:=]?\\s*" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern PH_AFTER = Pattern.compile(NUMBER + "\\s*p\\s*h\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEMPERATURE = Pattern.compile(
        "(?:t(?:emp)?\\s*[:=]\\s*" + NUMBER + "|" + NUMBER + "\\s*(?:°|º|deg)?\\s*c\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_NUMBER = Pattern.compile(NUMBER);

    private TextMeterParser() {
    }

    public static Optional<PhReading> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String text = line.trim();
        Double temperature = null;
        Matcher t = TEMPERATURE.matcher(text);
        String withoutTemperature = text;
        if (t.find()) {
            temperature = number(t.group(1) != null ? t.group(1) : t.group(2));
            withoutTemperature = text.substring(0, t.start()) + " " + text.substring(t.end());
        }
        Double ph = null;
        Matcher m = PH_BEFORE.matcher(withoutTemperature);
        if (m.find()) {
            ph = number(m.group(1));
        } else {
            m = PH_AFTER.matcher(withoutTemperature);
            if (m.find()) {
                ph = number(m.group(1));
            } else {
                Matcher any = ANY_NUMBER.matcher(withoutTemperature);
                if (any.find()) {
                    double first = number(any.group(1));
                    if (!any.find()) {
                        ph = first;
                    }
                }
            }
        }
        if (ph == null || ph < 0 || ph > 14) {
            return Optional.empty();
        }
        return Optional.of(new PhReading(Math.round(ph * 100) / 100.0, temperature, java.time.Instant.now()));
    }

    private static double number(String raw) {
        return Double.parseDouble(raw.replace(',', '.').toLowerCase(Locale.ROOT));
    }
}
