package com.aquaassistant.reminder;

import com.aquaassistant.notify.Notifier;
import com.aquaassistant.place.Place;
import com.aquaassistant.place.PlaceRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Every 5 minutes, sends the reminders that became due (once per due date). */
@Component
public class ReminderScheduler {

    private final ReminderRepository repository;
    private final PlaceRepository places;
    private final Notifier notifier;

    public ReminderScheduler(ReminderRepository repository, PlaceRepository places, Notifier notifier) {
        this.repository = repository;
        this.places = places;
        this.notifier = notifier;
    }

    @Scheduled(initialDelayString = "${aquasense.reminders.initial-delay-ms:60000}", fixedDelayString = "${aquasense.reminders.interval-ms:300000}")
    @Transactional
    public int sendDue() {
        Instant now = Instant.now();
        int sent = 0;
        for (Reminder reminder : repository.findTop200ByActiveTrueAndNextDueBeforeOrderByNextDueAsc(now.plusSeconds(1))) {
            if (!reminder.needsNotice(now)) {
                continue;
            }
            String place = reminder.getPlaceId() == null ? null : places.findById(reminder.getPlaceId()).map(Place::getName).orElse(null);
            String body = place == null ? "Toca para abrir Aqua Assistant AI." : "En " + place + ". Toca para abrir Aqua Assistant AI.";
            notifier.reminder(reminder.getUserId(), new Notifier.Message(reminder.getTitle(), body, "/#/dashboard",
                "Aqua Assistant AI - Recordatorio: " + reminder.getTitle(),
                List.of("Hoy toca: " + reminder.getTitle() + (place == null ? "." : " (" + place + ")."),
                    "Cuando lo hagas, márcalo como hecho en Aqua Assistant AI y el próximo se contará desde ese momento.",
                    "Puedes cambiar o borrar este recordatorio en la app.")), reminder.isByPush(), reminder.isByEmail());
            reminder.notified(now);
            sent++;
        }
        return sent;
    }
}
