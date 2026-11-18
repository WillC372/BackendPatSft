package com.aquaassistant.reminder;

import com.aquaassistant.audit.AuditService;
import com.aquaassistant.common.BusinessException;
import com.aquaassistant.place.Place;
import com.aquaassistant.place.PlaceAccess;
import com.aquaassistant.place.PlaceRepository;
import com.aquaassistant.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The person's reminders ("mide hoy", "limpia el filtro"...), with what is due now. */
@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private static final int MAX_REMINDERS = 50;

    public record ReminderRequest(
        @NotNull(message = "Elige el tipo de recordatorio.") ReminderKind kind,
        @Size(max = 80, message = "Máximo 80 caracteres.") String title,
        @NotNull(message = "Elige cada cuántos días.") @Min(value = 1, message = "Como mínimo cada día.")
        @Max(value = 365, message = "Como máximo cada 365 días.") Integer everyDays,
        Instant nextDue, Long placeId, boolean byEmail, boolean byPush, Boolean active) {
    }

    public record ReminderResponse(Long id, String kind, String title, int everyDays, Instant nextDue, boolean due,
                                   Long placeId, String placeName, boolean byEmail, boolean byPush, boolean active) {
    }

    private final ReminderRepository repository;
    private final PlaceAccess access;
    private final PlaceRepository places;
    private final AuditService audit;

    public ReminderController(ReminderRepository repository, PlaceAccess access, PlaceRepository places, AuditService audit) {
        this.repository = repository;
        this.access = access;
        this.places = places;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ReminderResponse> list() {
        Long userId = CurrentUser.id();
        Instant now = Instant.now();
        return repository.findByUserIdOrderByNextDueAsc(userId).stream().map(r -> toResponse(r, now)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReminderResponse create(@Valid @RequestBody ReminderRequest request) {
        Long userId = CurrentUser.id();
        if (repository.countByUserId(userId) >= MAX_REMINDERS) {
            throw BusinessException.conflict("Puedes tener hasta " + MAX_REMINDERS + " recordatorios.");
        }
        Long placeId = placeFor(userId, request.placeId());
        Reminder saved = repository.save(new Reminder(userId, placeId, request.kind(), titleOf(request),
            request.everyDays(), request.nextDue() == null ? Instant.now() : request.nextDue(), request.byEmail(), request.byPush()));
        audit.record(userId, "REMINDER_SAVED", saved.getTitle());
        return toResponse(saved, Instant.now());
    }

    @PutMapping("/{id}")
    @Transactional
    public ReminderResponse update(@PathVariable Long id, @Valid @RequestBody ReminderRequest request) {
        Long userId = CurrentUser.id();
        Reminder reminder = owned(userId, id);
        reminder.update(placeFor(userId, request.placeId()), request.kind(), titleOf(request), request.everyDays(),
            request.nextDue(), request.byEmail(), request.byPush(), request.active() == null || request.active());
        audit.record(userId, "REMINDER_SAVED", reminder.getTitle());
        return toResponse(reminder, Instant.now());
    }

    @PostMapping("/{id}/done")
    @Transactional
    public ReminderResponse done(@PathVariable Long id) {
        Long userId = CurrentUser.id();
        Reminder reminder = owned(userId, id);
        reminder.done(Instant.now());
        audit.record(userId, "REMINDER_DONE", reminder.getTitle());
        return toResponse(reminder, Instant.now());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Long userId = CurrentUser.id();
        Reminder reminder = owned(userId, id);
        repository.delete(reminder);
        audit.record(userId, "REMINDER_DELETED", reminder.getTitle());
    }

    private Reminder owned(Long userId, Long id) {
        return repository.findById(id).filter(r -> r.getUserId().equals(userId))
            .orElseThrow(() -> BusinessException.notFound("El recordatorio no existe."));
    }

    private Long placeFor(Long userId, Long placeId) {
        return placeId == null ? access.activeId(userId) : access.visible(userId, placeId).getId();
    }

    private static String titleOf(ReminderRequest request) {
        return request.title() == null || request.title().isBlank() ? request.kind().defaultTitle() : request.title().trim();
    }

    private ReminderResponse toResponse(Reminder r, Instant now) {
        String placeName = r.getPlaceId() == null ? null : places.findById(r.getPlaceId()).map(Place::getName).orElse(null);
        return new ReminderResponse(r.getId(), r.getKind().name(), r.getTitle(), r.getEveryDays(), r.getNextDue(), r.isDue(now),
            r.getPlaceId(), placeName, r.isByEmail(), r.isByPush(), r.isActive());
    }
}
