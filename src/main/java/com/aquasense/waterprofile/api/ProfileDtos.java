package com.aquasense.waterprofile.api;

import com.aquasense.waterprofile.domain.AcidityClass;
import com.aquasense.waterprofile.domain.PhClassification;
import com.aquasense.waterprofile.domain.RangeStatus;
import com.aquasense.waterprofile.domain.WaterProfile;
import com.aquasense.waterprofile.domain.WaterType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin("0") @DecimalMax("14") BigDecimal optimalMin,
            @NotNull @DecimalMin("0") @DecimalMax("14") BigDecimal optimalMax,
            @NotNull @DecimalMin("0") @DecimalMax("14") BigDecimal cautionMin,
            @NotNull @DecimalMin("0") @DecimalMax("14") BigDecimal cautionMax) {
    }

    public record DuplicateRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record ProfileResponse(UUID id, WaterType type, String name, String description, BigDecimal optimalMin,
                                  BigDecimal optimalMax, BigDecimal cautionMin, BigDecimal cautionMax,
                                  String sourceReference, boolean system) {

        public static ProfileResponse from(WaterProfile p) {
            return new ProfileResponse(p.getId(), p.getType(), p.getName(), p.getDescription(), p.getOptimalMin(),
                    p.getOptimalMax(), p.getCautionMin(), p.getCautionMax(), p.getSourceReference(), p.isSystem());
        }
    }

    public record ClassificationResponse(BigDecimal ph, AcidityClass acidity, RangeStatus status,
                                         boolean withinRecommendedRange, String explanation, String disclaimer) {

        public static ClassificationResponse from(PhClassification c, WaterProfile profile) {
            return new ClassificationResponse(c.ph(), c.acidity(), c.status(), c.withinRecommendedRange(),
                    explain(c, profile),
                    "El pH es solo un indicador: por sí solo no demuestra que el agua sea potable, segura ni "
                            + "esté libre de contaminantes.");
        }

        private static String explain(PhClassification c, WaterProfile p) {
            String range = p.getOptimalMin().stripTrailingZeros().toPlainString() + " a "
                    + p.getOptimalMax().stripTrailingZeros().toPlainString();
            return switch (c.status()) {
                case OPTIMAL -> "pH " + c.ph() + " dentro del rango recomendado (" + range + ") del perfil «"
                        + p.getName() + "».";
                case CAUTION -> "pH " + c.ph() + " fuera del rango recomendado (" + range + ") del perfil «"
                        + p.getName() + "», pero aún en zona de precaución. Conviene repetir la medición y revisar.";
                case CRITICAL -> "pH " + c.ph() + " muy alejado del rango recomendado (" + range + ") del perfil «"
                        + p.getName() + "». Se requiere verificación adicional.";
            };
        }
    }
}
