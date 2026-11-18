package com.aquaassistant.place.dto;

import com.aquaassistant.place.PlaceKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Requests and answers of the places API. */
public final class PlaceDtos {

    private PlaceDtos() {
    }

    public record PlaceRequest(
        @NotBlank(message = "Escribe un nombre para el lugar.") @Size(max = 80, message = "Máximo 80 caracteres.") String name,
        @NotNull(message = "Elige el tipo de agua.") PlaceKind kind,
        @DecimalMin(value = "1.0", message = "El volumen mínimo es 1 litro.")
        @DecimalMax(value = "100000000.0", message = "El volumen máximo es 100 millones de litros.") Double volumeLiters,
        Long profileId) {
    }

    public record ShareRequest(
        @NotNull @Pattern(regexp = "VIEWER|EDITOR", message = "Elige si podrán solo ver o también medir.") String role) {
    }

    public record JoinRequest(
        @NotBlank(message = "Escribe el código.") @Size(max = 20, message = "El código no es válido.") String code) {
    }

    public record PlaceResponse(Long id, String name, String kind, String kindLabel, Double volumeLiters, Long profileId,
                                String profileName, double idealMin, double idealMax, String role, String roleLabel,
                                boolean canEdit, boolean active, String ownerName, long members, String shareCode,
                                String shareRole, long measurements, Instant lastMeasuredAt) {
    }

    public record MemberResponse(Long userId, String name, String email, String role, String roleLabel, Instant joinedAt) {
    }
}
