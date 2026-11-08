package com.aquasense.admin;

import com.aquasense.admin.AdminDtos.ActivityRow;
import com.aquasense.admin.AdminDtos.Guards;
import com.aquasense.admin.AdminDtos.PaymentRow;
import com.aquasense.admin.AdminDtos.Payments;
import com.aquasense.admin.AdminDtos.PlanChange;
import com.aquasense.admin.AdminDtos.PlanInfo;
import com.aquasense.admin.AdminDtos.Stats;
import com.aquasense.admin.AdminDtos.Summary;
import com.aquasense.admin.AdminDtos.UserDetail;
import com.aquasense.admin.AdminDtos.UserPage;
import com.aquasense.admin.AdminDtos.UserRow;
import com.aquasense.audit.AuditEntry;
import com.aquasense.audit.AuditRepository;
import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.intelligence.ai.AiUsageRepository;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementRepository;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.payment.Payment;
import com.aquasense.payment.PaymentProperties;
import com.aquasense.payment.PaymentRepository;
import com.aquasense.payment.PaymentStatus;
import com.aquasense.profile.WaterProfileRepository;
import com.aquasense.user.AuthService;
import com.aquasense.user.Plan;
import com.aquasense.user.Role;
import com.aquasense.user.User;
import com.aquasense.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration: lookup and changes of plan, role and account status.
 *
 * <p>Rules the server always enforces, even though the UI already reflects them:
 * <ul>
 *   <li>An administrator cannot change their own role or disable their own account.</li>
 *   <li>The ADMIN_EMAIL account (main administrator) cannot lose the role or be disabled from the screen.</li>
 *   <li>At least one active administrator always remains.</li>
 *   <li>A disabled account cannot receive the administrator role.</li>
 * </ul>
 * Every change is audited twice: as an action of the administrator and in the history of the affected user.
 */
@Service
public class AdminUserService {

    static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
        Locale.forLanguageTag("es-CO")).withZone(ZoneId.of("America/Bogota"));

    /** Readable names of the audit actions. An action without a name is shown with its code. */
    static final Map<String, String> ACTION_LABELS = Map.ofEntries(
        Map.entry("USER_REGISTERED", "Creó su cuenta"),
        Map.entry("USER_LOGIN", "Inició sesión"),
        Map.entry("USER_LOGOUT", "Cerró sesión"),
        Map.entry("LOGIN_BLOCKED", "Intento de ingreso con la cuenta desactivada"),
        Map.entry("PASSWORD_RESET_REQUESTED", "Pidió restablecer la contraseña"),
        Map.entry("PASSWORD_RESET", "Restableció la contraseña"),
        Map.entry("SETTINGS_UPDATED", "Cambió su configuración"),
        Map.entry("MEASUREMENT_SAVED", "Guardó una medición"),
        Map.entry("MEASUREMENT_DELETED", "Borró una medición"),
        Map.entry("PROFILE_CREATED", "Creó un perfil de agua"),
        Map.entry("PROFILE_UPDATED", "Editó un perfil de agua"),
        Map.entry("PROFILE_DELETED", "Borró un perfil de agua"),
        Map.entry("PROFILE_DUPLICATED", "Duplicó un perfil de agua"),
        Map.entry("PROFILE_ACTIVATED", "Cambió el perfil activo"),
        Map.entry("REPORT_GENERATED", "Generó un informe"),
        Map.entry("AGENT_REPORT", "Pidió un informe al agente de IA"),
        Map.entry("AI_DIAGNOSIS", "Pidió un diagnóstico con IA"),
        Map.entry("PAYMENT_STARTED", "Inició un pago"),
        Map.entry("PREMIUM_PAID", "Pagó Premium"),
        Map.entry("PAYMENT_DECLINED", "Pago rechazado"),
        Map.entry("PAYMENT_MISMATCH", "Pago con datos que no coinciden"),
        Map.entry("PREMIUM_EXPIRED", "Venció su Premium"),
        Map.entry("ADMIN_VERIFICATION_SENT", "Se envió el enlace para confirmar el rol de administrador"),
        Map.entry("ADMIN_ROLE_CONFIRMED", "Confirmó el rol de administrador por correo"),
        Map.entry("ADMIN_VERIFICATION_FAILED", "No se pudo enviar el enlace para confirmar el rol de administrador"),
        Map.entry("ADMIN_PLAN_CHANGED", "Cambió el plan de un usuario"),
        Map.entry("ADMIN_ROLE_CHANGED", "Cambió el rol de un usuario"),
        Map.entry("ADMIN_USER_DISABLED", "Desactivó una cuenta"),
        Map.entry("ADMIN_USER_ENABLED", "Reactivó una cuenta"),
        Map.entry("ACCOUNT_PLAN_CHANGED", "Un administrador cambió su plan"),
        Map.entry("ACCOUNT_ROLE_CHANGED", "Un administrador cambió su rol"),
        Map.entry("ACCOUNT_DISABLED", "Un administrador desactivó la cuenta"),
        Map.entry("ACCOUNT_ENABLED", "Un administrador reactivó la cuenta"),
        Map.entry("MAIL_TEST_SENT", "Envió un correo de prueba"),
        Map.entry("MAIL_TEST_FAILED", "Falló el correo de prueba"));

    private final UserRepository users;
    private final MeasurementRepository measurements;
    private final WaterProfileRepository profiles;
    private final PaymentRepository payments;
    private final AiUsageRepository aiUsage;
    private final AuditRepository auditRepository;
    private final AuditService audit;
    private final AuthService authService;
    private final PaymentProperties paymentProperties;
    private final ApplicationEventPublisher events;

    public AdminUserService(UserRepository users, MeasurementRepository measurements, WaterProfileRepository profiles,
                            PaymentRepository payments, AiUsageRepository aiUsage, AuditRepository auditRepository,
                            AuditService audit, AuthService authService, PaymentProperties paymentProperties,
                            ApplicationEventPublisher events) {
        this.users = users;
        this.measurements = measurements;
        this.profiles = profiles;
        this.payments = payments;
        this.aiUsage = aiUsage;
        this.auditRepository = auditRepository;
        this.audit = audit;
        this.authService = authService;
        this.paymentProperties = paymentProperties;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        long premium = users.countByPlan(Plan.PREMIUM);
        long manual = users.countByPlanAndPremiumUntilIsNull(Plan.PREMIUM);
        Payments pay = new Payments(
            payments.countByStatusAndTestMode(PaymentStatus.APPROVED, false), payments.sumAmount(PaymentStatus.APPROVED, false),
            payments.countByStatusAndTestMode(PaymentStatus.APPROVED, true), payments.sumAmount(PaymentStatus.APPROVED, true),
            payments.countByStatus(PaymentStatus.PENDING), payments.countByStatus(PaymentStatus.DECLINED),
            paymentProperties.currency());
        PlanInfo plan = new PlanInfo(paymentProperties.price(), paymentProperties.currency(), paymentProperties.periodDays(),
            "PayU", paymentProperties.payu().test(), paymentProperties.enabled());
        return new Summary(users.count(), users.countByRole(Role.ADMIN), premium, manual, premium - manual,
            users.countByEnabledFalse(), users.countByCreatedAtAfter(weekAgo), users.countByLastLoginAtAfter(weekAgo),
            measurements.count(), measurements.countByMeasuredAtAfter(weekAgo), pay, plan);
    }

    @Transactional(readOnly = true)
    public UserPage list(Long adminId, String text, Role role, Plan plan, Boolean enabled, int page, int size) {
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        int safePage = Math.max(0, page);
        Page<User> result = users.findAll(UserFilters.all(text, role, plan, enabled),
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
        Map<Long, Long> counts = measurementCounts(result.getContent().stream().map(User::getId).toList());
        List<UserRow> rows = result.getContent().stream()
            .map(u -> row(u, counts.getOrDefault(u.getId(), 0L), adminId)).toList();
        return new UserPage(rows, result.getTotalElements(), safePage, safeSize, result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserDetail detail(Long adminId, Long userId) {
        User user = find(userId);
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (MeasurementSource source : MeasurementSource.values()) {
            bySource.put(source.name(), 0L);
        }
        for (Object[] r : measurements.countBySource(userId)) {
            bySource.put(((MeasurementSource) r[0]).name(), (Long) r[1]);
        }
        long total = bySource.values().stream().mapToLong(Long::longValue).sum();
        Instant last = measurements.findTopByUserIdOrderByMeasuredAtDesc(userId).map(Measurement::getMeasuredAt).orElse(null);
        long aiCalls = aiUsage.countByUserIdAndCachedFalseAndSuccessTrueAndCreatedAtAfter(userId,
            Instant.now().minus(Duration.ofDays(30)));
        Stats stats = new Stats(profiles.countByOwnerId(userId), total, bySource, last, aiCalls);
        List<PaymentRow> pays = payments.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10)).stream()
            .map(p -> paymentRow(p, user.getEmail())).toList();
        List<ActivityRow> activity = auditRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 15)).stream()
            .map(e -> activityRow(e, Map.of(userId, user.getEmail()))).toList();
        return new UserDetail(row(user, total, adminId), user.getDisabledReason(), user.getDisabledAt(), stats, pays, activity,
            new Guards(roleBlockedReason(adminId, user, user.getRole() == Role.ADMIN ? Role.USER : Role.ADMIN),
                statusBlockedReason(adminId, user, !user.isEnabled())));
    }

    @Transactional
    public UserDetail changePlan(Long adminId, Long userId, PlanChange change) {
        User user = find(userId);
        Instant until = change.plan() == Plan.PREMIUM && change.days() != null
            ? Instant.now().plus(Duration.ofDays(change.days())) : null;
        String before = planText(user.getPlan(), user.getPremiumUntil());
        user.changePlan(change.plan(), until);
        String after = planText(user.getPlan(), user.getPremiumUntil());
        record(adminId, user, "ADMIN_PLAN_CHANGED", "ACCOUNT_PLAN_CHANGED", "Plan: " + before + " → " + after);
        events.publishEvent(new AccountNotice(user.getEmail(), "Tu plan de AquaSense AI cambió", "Cambio de plan",
            List.of("Hola, " + user.getFullName() + ".", "Un administrador cambió tu plan de AquaSense AI.",
                "Plan anterior: " + before + ". Plan actual: " + after + ".")));
        return detail(adminId, userId);
    }

    @Transactional
    public UserDetail changeRole(Long adminId, Long userId, Role role) {
        User user = find(userId);
        if (user.getRole() == role) {
            return detail(adminId, userId);
        }
        String blocked = roleBlockedReason(adminId, user, role);
        if (blocked != null) {
            throw BusinessException.conflict(blocked);
        }
        if (role == Role.ADMIN) {
            user.promoteToAdmin();
        } else {
            user.demoteToUser();
        }
        String label = role == Role.ADMIN ? "Administrador" : "Usuario";
        record(adminId, user, "ADMIN_ROLE_CHANGED", "ACCOUNT_ROLE_CHANGED", "Rol: " + label);
        events.publishEvent(new AccountNotice(user.getEmail(), "Tu rol en AquaSense AI cambió", "Cambio de rol",
            List.of("Hola, " + user.getFullName() + ".", "Un administrador cambió tu rol en AquaSense AI a: " + label + ".",
                "Por seguridad se cerraron tus sesiones abiertas. Vuelve a iniciar sesión para continuar.")));
        return detail(adminId, userId);
    }

    @Transactional
    public UserDetail changeStatus(Long adminId, Long userId, boolean enabled, String reason) {
        User user = find(userId);
        if (user.isEnabled() == enabled) {
            return detail(adminId, userId);
        }
        String blocked = statusBlockedReason(adminId, user, enabled);
        if (blocked != null) {
            throw BusinessException.conflict(blocked);
        }
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (enabled) {
            user.enable();
            record(adminId, user, "ADMIN_USER_ENABLED", "ACCOUNT_ENABLED", "Cuenta reactivada");
            events.publishEvent(new AccountNotice(user.getEmail(), "Tu cuenta de AquaSense AI está activa", "Cuenta reactivada",
                List.of("Hola, " + user.getFullName() + ".", "Un administrador reactivó tu cuenta. Ya puedes iniciar sesión.")));
        } else {
            user.disable(cleanReason, Instant.now());
            record(adminId, user, "ADMIN_USER_DISABLED", "ACCOUNT_DISABLED",
                "Cuenta desactivada" + (cleanReason == null ? "" : ". Motivo: " + cleanReason));
            List<String> text = new ArrayList<>(List.of("Hola, " + user.getFullName() + ".",
                "Un administrador desactivó tu cuenta de AquaSense AI. Mientras siga así no podrás iniciar sesión; tus datos se conservan."));
            if (cleanReason != null) {
                text.add("Motivo: " + cleanReason);
            }
            text.add("Si crees que es un error, responde a este correo o escribe al administrador.");
            events.publishEvent(new AccountNotice(user.getEmail(), "Tu cuenta de AquaSense AI fue desactivada", "Cuenta desactivada",
                text));
        }
        return detail(adminId, userId);
    }

    @Transactional(readOnly = true)
    public List<PaymentRow> recentPayments(int limit) {
        List<Payment> list = payments.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(100, limit))));
        Map<Long, String> emails = emails(list.stream().map(Payment::getUserId).collect(Collectors.toSet()));
        return list.stream().map(p -> paymentRow(p, emails.get(p.getUserId()))).toList();
    }

    /** Administration actions (plan, role and status changes, and the confirmation of the main administrator). */
    @Transactional(readOnly = true)
    public List<ActivityRow> adminActivity(int limit) {
        List<AuditEntry> list = auditRepository.findByActionStartingWithOrderByCreatedAtDesc("ADMIN_",
            PageRequest.of(0, Math.max(1, Math.min(100, limit))));
        Map<Long, String> emails = emails(list.stream().map(AuditEntry::getUserId).filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet()));
        return list.stream().map(e -> activityRow(e, emails)).toList();
    }

    /** Reason why the user cannot be moved to {@code target}, or {@code null} if it is allowed. */
    String roleBlockedReason(Long adminId, User user, Role target) {
        if (user.getId().equals(adminId)) {
            return "No puedes cambiar tu propio rol.";
        }
        if (target == Role.ADMIN && !user.isEnabled()) {
            return "La cuenta está desactivada: reactívala antes de darle el rol de administrador.";
        }
        if (target == Role.USER && user.getRole() == Role.ADMIN) {
            if (authService.isAdminEmail(user.getEmail())) {
                return "Es el administrador principal configurado en el servidor (ADMIN_EMAIL).";
            }
            if (users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1 && user.isEnabled()) {
                return "Debe quedar al menos un administrador activo.";
            }
        }
        return null;
    }

    /** Reason why the account cannot be enabled ({@code enable}) or disabled, or {@code null} if it is allowed. */
    String statusBlockedReason(Long adminId, User user, boolean enable) {
        if (enable) {
            return null;
        }
        if (user.getId().equals(adminId)) {
            return "No puedes desactivar tu propia cuenta.";
        }
        if (user.getRole() == Role.ADMIN) {
            if (authService.isAdminEmail(user.getEmail())) {
                return "Es el administrador principal configurado en el servidor (ADMIN_EMAIL).";
            }
            if (users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
                return "Debe quedar al menos un administrador activo.";
            }
        }
        return null;
    }

    private void record(Long adminId, User target, String adminAction, String userAction, String detail) {
        audit.record(adminId, adminAction, target.getEmail() + " (#" + target.getId() + ") · " + detail);
        audit.record(target.getId(), userAction, detail);
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> BusinessException.notFound("Usuario no encontrado."));
    }

    private UserRow row(User u, long measurementCount, Long adminId) {
        return new UserRow(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), u.getPlan().name(), u.getPremiumUntil(),
            u.isManualPremium(), u.isEnabled(), u.getCreatedAt(), u.getLastLoginAt(), measurementCount, u.getId().equals(adminId),
            authService.isAdminEmail(u.getEmail()));
    }

    private Map<Long, Long> measurementCounts(Collection<Long> ids) {
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] r : measurements.countByUserIds(ids)) {
                counts.put((Long) r[0], (Long) r[1]);
            }
        }
        return counts;
    }

    private Map<Long, String> emails(Set<Long> ids) {
        return ids.isEmpty() ? Map.of()
            : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getEmail));
    }

    private static PaymentRow paymentRow(Payment p, String email) {
        return new PaymentRow(p.getId(), p.getUserId(), email, p.getReferenceCode(), p.getAmount(), p.getCurrency(),
            p.getPeriodDays(), p.getStatus().name(), p.getStatus().label(), p.getPaymentMethod(), p.isTestMode(),
            p.getCreatedAt(), p.getPremiumUntil());
    }

    private static ActivityRow activityRow(AuditEntry e, Map<Long, String> emails) {
        return new ActivityRow(e.getId(), e.getUserId(), e.getUserId() == null ? null : emails.get(e.getUserId()), e.getAction(),
            ACTION_LABELS.getOrDefault(e.getAction(), e.getAction()), e.getDetail(), e.getCreatedAt());
    }

    private static String planText(Plan plan, Instant until) {
        if (plan == Plan.FREE) {
            return "Gratuito";
        }
        return until == null ? "Premium sin vencimiento" : "Premium hasta el " + LONG_DATE.format(until);
    }
}
