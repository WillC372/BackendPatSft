package com.aquasense.admin;

import com.aquasense.user.Plan;
import com.aquasense.user.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contracts of the administration API. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Dashboard figures. Test (sandbox) payments are counted apart from real ones so they are not confused. */
    public record Summary(long users, long admins, long premium, long premiumManual, long premiumPaid, long disabled,
                          long newLast7Days, long activeLast7Days, long measurements, long measurementsLast7Days,
                          Payments payments, PlanInfo plan) {
    }

    public record Payments(long approvedReal, BigDecimal amountReal, long approvedTest, BigDecimal amountTest, long pending,
                           long declined, String currency) {
    }

    public record PlanInfo(BigDecimal price, String currency, int periodDays, String provider, boolean testMode,
                           boolean paymentsEnabled) {
    }

    /** Row of the user list. {@code self}: it is the account of the administrator making the request. */
    public record UserRow(Long id, String email, String fullName, String role, String plan, Instant premiumUntil,
                          boolean manualPremium, boolean enabled, Instant createdAt, Instant lastLoginAt, long measurements,
                          boolean self, boolean configuredAdmin) {
    }

    public record UserPage(List<UserRow> items, long total, int page, int size, int totalPages) {
    }

    public record UserDetail(UserRow user, String disabledReason, Instant disabledAt, Stats stats, List<PaymentRow> payments,
                             List<ActivityRow> activity, Guards guards) {
    }

    public record Stats(long profiles, long measurements, Map<String, Long> measurementsBySource, Instant lastMeasurementAt,
                        long aiCallsLast30Days) {
    }

    /**
     * Which actions the server allows on this account and, if not, why. The UI disables the buttons with this
     * reason; the server checks again when it receives the change.
     */
    public record Guards(String roleBlockedReason, String statusBlockedReason, String deleteBlockedReason) {
    }

    public record PaymentRow(Long id, Long userId, String userEmail, String referenceCode, BigDecimal amount, String currency,
                             int periodDays, String status, String statusLabel, String paymentMethod, boolean testMode,
                             Instant createdAt, Instant premiumUntil) {
    }

    public record ActivityRow(Long id, Long userId, String userEmail, String action, String label, String detail,
                              Instant createdAt) {
    }

    public record PlanChange(@NotNull(message = "Indica el plan.") Plan plan,
                             @Min(value = 1, message = "La duración mínima es 1 día.")
                             @Max(value = 730, message = "La duración máxima es 730 días.") Integer days) {
    }

    public record RoleChange(@NotNull(message = "Indica el rol.") Role role) {
    }

    public record StatusChange(@NotNull(message = "Indica si la cuenta queda activa.") Boolean enabled,
                               @Size(max = 300, message = "El motivo admite hasta 300 caracteres.") String reason) {
    }
}
