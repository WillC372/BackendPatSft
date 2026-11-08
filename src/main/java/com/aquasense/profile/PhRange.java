package com.aquasense.profile;

import com.aquasense.common.BusinessException;

public record PhRange(double idealMin, double idealMax, double cautionMin, double cautionMax) {

    public void validate() {
        if (cautionMin < 0 || cautionMax > 14) {
            throw BusinessException.badRequest("Los rangos deben estar entre 0 y 14.");
        }
        if (!(cautionMin <= idealMin && idealMin < idealMax && idealMax <= cautionMax)) {
            throw BusinessException.badRequest(
                "Orden inválido: precaución mínima <= ideal mínimo < ideal máximo <= precaución máxima.");
        }
    }
}
