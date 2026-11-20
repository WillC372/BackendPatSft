package com.aquaassistant.notify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Emails only go to addresses that can receive them (H-058). */
class DeliverableEmailTest {

    @Test
    void madeUpAndExampleAddressesReceiveNoEmail() {
        assertThat(Notifier.deliverable("premium@demo.aquaassistant")).isFalse();
        assertThat(Notifier.deliverable("docente.aqua@example.com")).isFalse();
        assertThat(Notifier.deliverable("persona@aquasense.test")).isFalse();
        assertThat(Notifier.deliverable("candow372@gmail.com")).isTrue();
    }
}
