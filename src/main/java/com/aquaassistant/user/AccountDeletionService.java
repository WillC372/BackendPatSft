package com.aquaassistant.user;

import com.aquaassistant.payment.PaymentRepository;
import com.aquaassistant.payment.PaymentStatus;
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
    private final jakarta.persistence.EntityManager entities;
    private final com.aquaassistant.place.PlaceAccess places;

    public AccountDeletionService(UserRepository users, PaymentRepository payments, jakarta.persistence.EntityManager entities,
                                  com.aquaassistant.place.PlaceAccess places) {
        this.users = users;
        this.payments = payments;
        this.entities = entities;
        this.places = places;
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
        // Other people's places and measurements that use one of this user's profiles (an editor of a shared place)
        // go to the general profile first; otherwise deleting the profiles with the account fails (H-002).
        Long general = places.defaultProfile().getId();
        String mine = "(select w.id from WaterProfile w where w.ownerId = :userId)";
        entities.createQuery("update Place p set p.profileId = :general where p.profileId in " + mine + " and p.ownerId <> :userId")
            .setParameter("general", general).setParameter("userId", user.getId()).executeUpdate();
        entities.createQuery("update Measurement m set m.profileId = :general where m.profileId in " + mine + " and m.userId <> :userId")
            .setParameter("general", general).setParameter("userId", user.getId()).executeUpdate();
        users.delete(user);
        users.flush();
    }
}
