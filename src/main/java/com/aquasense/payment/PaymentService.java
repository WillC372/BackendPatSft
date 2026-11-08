package com.aquasense.payment;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.user.MailService;
import com.aquasense.user.Plan;
import com.aquasense.user.User;
import com.aquasense.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pago de Premium por periodo con PayU (sin cobros automáticos ni medios de pago guardados).
 *
 * <ol>
 *   <li>{@code checkout}: crea un pago PENDIENTE con referencia única y devuelve el formulario firmado.</li>
 *   <li>El usuario paga en la página de PayU; AquaSense nunca ve la tarjeta.</li>
 *   <li>{@code verify} (al volver) y {@code confirm} (webhook): el servidor consulta la orden en PayU y comprueba cuenta,
 *       valor y moneda. Solo entonces activa Premium, una sola vez por pago (idempotente).</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"))
        .withZone(ZoneId.of("America/Bogota"));
    static final Duration PENDING_LIFETIME = Duration.ofHours(24);

    private final PaymentRepository repository;
    private final UserRepository userRepository;
    private final PayuGateway gateway;
    private final PaymentProperties config;
    private final AuditService auditService;
    private final MailService mailService;

    public PaymentService(PaymentRepository repository, UserRepository userRepository, PayuGateway gateway,
                          PaymentProperties config, AuditService auditService, MailService mailService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.config = config;
        this.auditService = auditService;
        this.mailService = mailService;
    }

    @Transactional
    public PaymentDtos.PlanStatus status(Long userId) {
        User user = user(userId);
        if (user.expirePremiumIfDue(Instant.now())) {
            auditService.record(userId, "PREMIUM_EXPIRED", "Terminó el periodo pagado");
        }
        boolean test = gateway.isTest();
        return new PaymentDtos.PlanStatus(user.getPlan().name(), user.getPremiumUntil(),
            user.getPlan() == Plan.PREMIUM && user.getPremiumUntil() == null, config.enabled(), config.price(),
            config.currency(), config.periodDays(), "PayU Latam", test,
            test ? List.of(
                new PaymentDtos.TestCase("APPROVED", "4097440000000004", "APPROVED", "777", "05/2030"),
                new PaymentDtos.TestCase("DECLINED", "4097440000000004", "REJECTED", "666", "07/2030")) : List.of(),
            test ? "Modo de pruebas de PayU: no se cobra dinero real. Usa los datos de prueba publicados por PayU." : null);
    }

    @Transactional
    public PaymentDtos.CheckoutResponse checkout(Long userId) {
        if (!config.enabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Los pagos no están habilitados en este servidor.");
        }
        User user = user(userId);
        if (user.getPlan() == Plan.PREMIUM && user.getPremiumUntil() == null) {
            throw BusinessException.conflict("Tu cuenta ya tiene Premium asignado por un administrador, sin vencimiento.");
        }
        String reference = "AQS" + userId + "-" + randomCode(10);
        Payment payment = repository.save(new Payment(userId, PayuGateway.PROVIDER, reference, config.price(), config.currency(),
            config.periodDays(), gateway.isTest()));
        String description = "AquaSense AI Premium " + config.periodDays() + " dias";
        Map<String, String> fields = gateway.formFields(payment, description, user.getEmail(), config.responseUrl(),
            config.confirmationUrl());
        auditService.record(userId, "PAYMENT_STARTED", reference + (gateway.isTest() ? " (pruebas)" : ""));
        return new PaymentDtos.CheckoutResponse(gateway.checkoutUrl(), fields, reference, payment.getAmount(),
            payment.getCurrency(), gateway.isTest());
    }

    /** El usuario volvió de PayU: se consulta la orden en PayU, nunca se confía en los parámetros de la URL. */
    @Transactional
    public PaymentDtos.PaymentView verify(Long userId, String referenceCode) {
        Payment payment = repository.findByReferenceCode(referenceCode).filter(p -> p.getUserId().equals(userId))
            .orElseThrow(() -> BusinessException.notFound("El pago no existe."));
        return PaymentDtos.PaymentView.of(payment, reconcile(payment));
    }

    /** Página de confirmación de PayU (webhook). Se valida la firma y luego se consulta la orden igual que en verify. */
    @Transactional
    public boolean confirm(Map<String, String> params) {
        if (!gateway.validConfirmation(params)) {
            LOG.warn("PayU confirmation rejected: invalid signature for reference {}", params.get("reference_sale"));
            return false;
        }
        repository.findByReferenceCode(params.getOrDefault("reference_sale", "")).ifPresent(this::reconcile);
        return true;
    }

    String reconcile(Payment payment) {
        if (payment.getStatus() == PaymentStatus.APPROVED) {
            return "Pago aprobado. Premium activo hasta el " + DATE.format(payment.getPremiumUntil() == null
                ? Instant.now() : payment.getPremiumUntil()) + ".";
        }
        Optional<PayuGateway.OrderResult> found;
        try {
            found = gateway.findOrder(payment.getReferenceCode());
        } catch (PayuGateway.GatewayException ex) {
            return "No se pudo consultar PayU en este momento (" + ex.getMessage() + "). Intenta verificar de nuevo en unos minutos.";
        }
        if (found.isEmpty()) {
            return "PayU todavía no registra un pago con esta referencia. Si acabas de pagar, espera un momento y verifica de nuevo.";
        }
        PayuGateway.OrderResult order = found.get();
        if (!gateway.belongsToMerchant(order) || order.value() == null || order.value().compareTo(payment.getAmount()) != 0
            || !payment.getCurrency().equals(order.currency())) {
            LOG.error("PayU order {} does not match payment {} (account {}, value {} {})", order.orderId(),
                payment.getReferenceCode(), order.accountId(), order.value(), order.currency());
            payment.update(PaymentStatus.ERROR, order.orderId(), order.transactionId(), "MISMATCH", order.paymentMethod());
            auditService.record(payment.getUserId(), "PAYMENT_MISMATCH", payment.getReferenceCode());
            return "El pago informado por PayU no coincide con el pedido (valor, moneda o comercio). No se activó Premium.";
        }
        PaymentStatus status = order.status();
        payment.update(status, order.orderId(), order.transactionId(), order.state(), order.paymentMethod());
        return switch (status) {
            case APPROVED -> applyPremium(payment);
            case DECLINED -> {
                auditService.record(payment.getUserId(), "PAYMENT_DECLINED", payment.getReferenceCode());
                yield "PayU rechazó el pago. No se hizo ningún cobro y tu plan no cambió.";
            }
            case ERROR -> "PayU informó un error en la transacción. Tu plan no cambió.";
            case EXPIRED -> "El pago venció sin completarse. Tu plan no cambió.";
            default -> "El pago está en proceso en PayU. Verifica de nuevo en unos minutos.";
        };
    }

    private String applyPremium(Payment payment) {
        User user = user(payment.getUserId());
        Instant until = user.extendPremium(Duration.ofDays(payment.getPeriodDays()), Instant.now());
        payment.markApplied(until);
        auditService.record(user.getId(), "PREMIUM_PAID", payment.getReferenceCode()
            + (until == null ? " (Premium ya asignado sin vencimiento)" : " hasta " + until));
        if (until == null) {
            return "Pago aprobado. Tu cuenta ya tenía Premium sin vencimiento asignado por un administrador.";
        }
        mailService.sendNotice(user.getEmail(), "AquaSense AI - Pago aprobado", "Pago aprobado", List.of(
            "Recibimos tu pago de Premium (referencia " + payment.getReferenceCode() + ").",
            "Premium está activo hasta el " + DATE.format(until) + ". Antes de esa fecha te avisaremos para que lo renueves.",
            payment.isTestMode() ? "Este pago se hizo en el modo de pruebas de PayU: no se cobró dinero real." : ""));
        return "Pago aprobado. Premium activo hasta el " + DATE.format(until) + ".";
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentView> history(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20)).stream()
            .map(p -> PaymentDtos.PaymentView.of(p, null)).toList();
    }

    /** Cada hora: Premium vencido vuelve a Gratuito y los pagos abandonados por más de 24 h se marcan vencidos. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void housekeeping() {
        Instant now = Instant.now();
        userRepository.findAll().forEach(user -> {
            if (user.expirePremiumIfDue(now)) {
                auditService.record(user.getId(), "PREMIUM_EXPIRED", "Terminó el periodo pagado");
            } else if (user.getPlan() == Plan.PREMIUM && user.getPremiumUntil() != null
                && user.getPremiumUntil().isBefore(now.plus(Duration.ofDays(config.reminderDays())))
                && !user.getPremiumUntil().equals(user.getPremiumReminderFor())) {
                boolean sent = mailService.sendNotice(user.getEmail(), "AquaSense AI - Tu Premium vence pronto", "Tu Premium vence pronto",
                    List.of("Tu plan Premium vence el " + DATE.format(user.getPremiumUntil()) + ".",
                        "Para conservar el análisis de 365 días, la predicción a 7 días y más consultas de IA, renueva desde Configuración → Plan."));
                if (sent) {
                    user.markPremiumReminderSent();
                }
            }
        });
        repository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, now.minus(PENDING_LIFETIME)).forEach(Payment::expire);
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("La cuenta no existe."));
    }

    static String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
