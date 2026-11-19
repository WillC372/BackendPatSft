package com.aquaassistant.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aquaassistant.user.Role;
import com.aquaassistant.user.User;
import com.aquaassistant.user.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * H-001: the return from PayU and its confirmation can reconcile the same payment at the same time. Premium must be
 * applied once: the second one waits for the first and finds the payment already approved.
 */
@SpringBootTest
class PaymentConcurrencyTest {

    @MockitoBean private PayuGateway gateway;
    @Autowired private PaymentService service;
    @Autowired private PaymentRepository payments;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anApprovedPaymentExtendsPremiumOnceEvenIfVerifiedTwiceAtTheSameTime() throws Exception {
        User user = users.save(new User("pago-" + UUID.randomUUID() + "@aquasense.test", "Pago", "x", Role.USER));
        String reference = "REF-" + UUID.randomUUID();
        payments.save(new Payment(user.getId(), "PAYU", reference, new BigDecimal("49900"), "COP", 30, true));
        when(gateway.belongsToMerchant(any())).thenReturn(true);
        when(gateway.findOrder(reference)).thenAnswer(inv -> {
            Thread.sleep(400); // PayU answers slowly: both requests are in flight at once
            return Optional.of(new PayuGateway.OrderResult("o1", "512322", "t1", "APPROVED", new BigDecimal("49900"), "COP", "VISA"));
        });

        CompletableFuture<?> a = CompletableFuture.runAsync(() -> service.verify(user.getId(), reference));
        CompletableFuture<?> b = CompletableFuture.runAsync(() -> service.verify(user.getId(), reference));
        CompletableFuture.allOf(a, b).get();

        Instant until = users.findById(user.getId()).orElseThrow().getPremiumUntil();
        assertThat(until).isBetween(Instant.now().plus(Duration.ofDays(29)), Instant.now().plus(Duration.ofDays(31)));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id = ? and action = 'PREMIUM_PAID'",
            Integer.class, user.getId())).isEqualTo(1);
    }
}
