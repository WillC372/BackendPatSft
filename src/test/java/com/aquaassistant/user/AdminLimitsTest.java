package com.aquaassistant.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminLimitsTest {

    @Test
    void anAdministratorHasNoLimitsAndAPermanentPremiumPlan() {
        User user = new User("ana@correo.com", "Ana", "hash", Role.USER);
        assertThat(user.isUnlimited()).isFalse();
        assertThat(user.getPlan()).isEqualTo(Plan.FREE);

        user.promoteToAdmin();

        assertThat(user.isUnlimited()).isTrue();
        assertThat(user.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(user.getPremiumUntil()).isNull();
        assertThat(user.isManualPremium()).isTrue();
    }
}
