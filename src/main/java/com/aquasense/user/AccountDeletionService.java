package com.aquasense.user;

import com.aquasense.payment.PaymentRepository;
import com.aquasense.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permanent deletion of an account. The database removes everything that belongs to the user in cascade
 * (profiles, measurements, alerts, diagnoses, reports, chats, AI usage, payments, tokens and audit entries).
 *
 * <p>An account is kept when deleting it would lose something that must not be lost: the last active administrator,
 * or approved payments with real money (test payments from the PayU sandbox do not count).
 */
@Service
public class AccountDeletionService {

    private final UserRepository users;
    private final PaymentRepository payments;

    public AccountDeletionService(UserRepository users, PaymentRepository payments) {
        this.users = users;
        this.payments = payments;
    }

    /** Reason why the account cannot be deleted, or {@code null} if it can. */
    @Transactional(readOnly = true)
    public String blockedReason(User user) {
        if (user.getRole() == Role.ADMIN && user.isEnabled() && users.countByRoleAndEnabledTrueAndDemoFalse(Role.ADMIN) <= 1) {
            return "Es el único administrador activo: asigna otro administrador antes de eliminarla.";
        }
        if (payments.existsByUserIdAndStatusAndTestMode(user.getId(), PaymentStatus.APPROVED, false)) {
            return "Tiene pagos reales aprobados; desactívala en lugar de eliminarla para conservar el registro.";
        }
        return null;
    }

    @Transactional
    public void delete(User user) {
        users.delete(user);
        users.flush();
    }
}
