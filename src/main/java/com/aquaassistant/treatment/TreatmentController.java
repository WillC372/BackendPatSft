package com.aquaassistant.treatment;

import com.aquaassistant.audit.AuditService;
import com.aquaassistant.common.BusinessException;
import com.aquaassistant.place.Place;
import com.aquaassistant.place.PlaceAccess;
import com.aquaassistant.security.CurrentUser;
import com.aquaassistant.user.User;
import com.aquaassistant.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Treatment log of the active place: what was added or done to the water, shown next to the measurements. */
@RestController
@RequestMapping("/api/v1/treatments")
public class TreatmentController {

    public record TreatmentRequest(
        @NotNull(message = "Elige qué aplicaste.") TreatmentProduct product,
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero.")
        @DecimalMax(value = "1000000", message = "La cantidad es demasiado grande.") Double amount,
        @Pattern(regexp = "g|kg|mL|L|pastillas|%", message = "Elige una unidad válida.") String unit,
        Instant appliedAt,
        @Size(max = 300, message = "Máximo 300 caracteres.") String note) {
    }

    public record TreatmentResponse(Long id, Long placeId, Instant appliedAt, String product, String productLabel,
                                    Double amount, String unit, String note, String byName, boolean mine) {
    }

    private final TreatmentRepository repository;
    private final PlaceAccess places;
    private final UserRepository users;
    private final AuditService audit;

    public TreatmentController(TreatmentRepository repository, PlaceAccess places, UserRepository users, AuditService audit) {
        this.repository = repository;
        this.places = places;
        this.users = users;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TreatmentResponse> list(@RequestParam(defaultValue = "30") int days) {
        Long userId = CurrentUser.id();
        Instant now = Instant.now();
        int window = Math.min(Math.max(days, 1), 3650);
        return repository.findTop200ByPlaceIdAndAppliedAtBetweenOrderByAppliedAtDesc(places.activeId(userId),
            now.minus(Duration.ofDays(window)), now.plus(Duration.ofMinutes(5))).stream().map(t -> toResponse(userId, t)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TreatmentResponse create(@Valid @RequestBody TreatmentRequest request) {
        Long userId = CurrentUser.id();
        Place place = places.editable(userId, places.activeId(userId));
        Instant at = request.appliedAt() == null ? Instant.now() : request.appliedAt();
        if (at.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            throw BusinessException.badRequest("La fecha no puede estar en el futuro.");
        }
        if ((request.amount() == null) != (request.unit() == null || request.unit().isBlank())) {
            throw BusinessException.badRequest("Escribe la cantidad junto con su unidad, o deja ambas vacías.");
        }
        Treatment saved = repository.save(new Treatment(place.getId(), userId, at, request.product(), request.amount(),
            request.unit() == null || request.unit().isBlank() ? null : request.unit(), blankToNull(request.note())));
        audit.record(userId, "TREATMENT_SAVED", request.product().label() + " en " + place.getName());
        return toResponse(userId, saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Long userId = CurrentUser.id();
        Treatment treatment = repository.findById(id).orElseThrow(() -> BusinessException.notFound("El tratamiento no existe."));
        places.editable(userId, treatment.getPlaceId());
        repository.delete(treatment);
        audit.record(userId, "TREATMENT_DELETED", treatment.getProduct().label());
    }

    private TreatmentResponse toResponse(Long userId, Treatment t) {
        String by = t.getUserId().equals(userId) ? null : users.findById(t.getUserId()).map(User::getFullName).orElse(null);
        return new TreatmentResponse(t.getId(), t.getPlaceId(), t.getAppliedAt(), t.getProduct().name(), t.getProduct().label(),
            t.getAmount(), t.getUnit(), t.getNote(), by, t.getUserId().equals(userId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
