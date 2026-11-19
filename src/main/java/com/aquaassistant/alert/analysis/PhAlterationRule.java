package com.aquaassistant.alert.analysis;

import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.profile.PhStatus;
import com.aquaassistant.profile.ProfileType;
import com.aquaassistant.profile.WaterProfile;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Alteration of the pH: the water was one way and, within the last hour, it changed by half a unit or more (something
 * fell into it, a dosing pump failed...), or it went into the critical level of its profile. It compares with every
 * measurement of the last hour, not only the previous one, so a meter that measures every few seconds also detects a
 * change that builds up over several minutes.
 *
 * <p>Whether it is dangerous is decided with the ranges of the profile: critical means dangerous. The message says so
 * and what to do for that kind of water; the app reads it aloud with the alarm.
 */
@Component
@Order(2)
public class PhAlterationRule implements AnomalyRule {

    public static final String TYPE = "PH_ALTERATION";
    public static final String TITLE = "Alteración del pH en el agua";
    static final double CHANGE = 0.5;
    static final Duration WINDOW = Duration.ofHours(1);

    @Override
    public Optional<Anomaly> evaluate(Measurement current, List<Measurement> previous, WaterProfile profile) {
        List<Measurement> recent = previous.stream().filter(m -> {
            Duration age = Duration.between(m.getMeasuredAt(), current.getMeasuredAt());
            return !age.isNegative() && age.compareTo(WINDOW) <= 0;
        }).toList();
        if (recent.isEmpty()) {
            return Optional.empty();
        }
        Measurement last = recent.get(recent.size() - 1);
        Measurement farthest = recent.stream()
            .max(Comparator.comparingDouble((Measurement m) -> Math.abs(current.getPh() - m.getPh()))).orElse(last);
        boolean dangerous = current.getStatus() == PhStatus.CRITICAL;
        boolean bigChange = Math.abs(current.getPh() - farthest.getPh()) >= CHANGE;
        boolean becameDangerous = dangerous && last.getStatus() != PhStatus.CRITICAL;
        if (!bigChange && !becameDangerous) {
            return Optional.empty();
        }
        Measurement from = bigChange ? farthest : last;
        String message = change(from, current) + " " + danger(current, profile);
        return Optional.of(new Anomaly(TYPE, dangerous ? Anomaly.Severity.CRITICAL : Anomaly.Severity.CAUTION, TITLE, message,
            current.getId(), current.getMeasuredAt(), current.getPh()));
    }

    static String change(Measurement from, Measurement current) {
        long minutes = Duration.between(from.getMeasuredAt(), current.getMeasuredAt()).toMinutes();
        String time = minutes < 1 ? "menos de un minuto" : minutes == 1 ? "1 minuto" : minutes + " minutos";
        return String.format(Locale.US, "El pH %s de %.2f a %.2f en %s.", current.getPh() > from.getPh() ? "subió" : "bajó",
            from.getPh(), current.getPh(), time);
    }

    static String danger(Measurement current, WaterProfile profile) {
        ProfileType type = profile.getType() == null ? ProfileType.GENERAL : profile.getType();
        return switch (current.getStatus()) {
            case CRITICAL -> String.format(Locale.US, "Es peligroso: está en nivel crítico para %s (fuera de %.1f - %.1f). ",
                profile.getName(), profile.getCautionMin(), profile.getCautionMax()) + whatToDo(type, true);
            case CAUTION -> String.format(Locale.US, "Por ahora no es peligroso, pero ya está fuera del rango ideal para %s (%s). ",
                profile.getName(), profile.rangeLabel()) + whatToDo(type, false);
            case OPTIMAL -> String.format(Locale.US, "Por ahora no es peligroso: sigue en el rango ideal para %s (%s), pero un "
                + "cambio tan rápido indica que algo entró al agua o que el medidor falla. Vuelve a medir en unos minutos.",
                profile.getName(), profile.rangeLabel());
        };
    }

    static String whatToDo(ProfileType type, boolean dangerous) {
        if (dangerous) {
            return switch (type) {
                case POOL -> "Por precaución, que nadie se bañe: salgan del agua y no vuelvan a entrar hasta corregir el pH y volver a medir.";
                case AQUARIUM -> "Los peces corren peligro: revisa qué cayó al agua y haz un cambio parcial con agua de pH parecido al de siempre; no corrijas con productos de golpe.";
                case DRINKING -> "No tomes esta agua ni la uses para cocinar hasta revisarla.";
                case IRRIGATION -> "No riegues con esta agua hasta revisarla y corregirla.";
                default -> "No uses esta agua hasta revisarla.";
            };
        }
        return switch (type) {
            case POOL -> "Evita bañarte hasta volver a medir y corrige con la calculadora de dosis.";
            case AQUARIUM -> "Vigila a los peces, vuelve a medir y corrige poco a poco.";
            default -> "Vuelve a medir en unos minutos y corrige si sigue así.";
        };
    }
}
