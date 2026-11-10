package com.aquasense.user;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.security.CurrentUser;
import com.aquasense.user.mail.MailDeliveryException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email delivery status and test email (ADMIN role). The test is only sent to the email of the administrator
 * who requests it, so the endpoint cannot be used to send emails to third parties.
 */
@RestController
@RequestMapping("/api/v1/admin/mail")
public class AdminMailController {

    private final MailService mailService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    private final DemoAccounts demo;

    public AdminMailController(MailService mailService, UserRepository userRepository, AuditService auditService,
                               DemoAccounts demo) {
        this.demo = demo;
        this.mailService = mailService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public MailService.Status status() {
        return mailService.status();
    }

    @PostMapping("/test")
    public Map<String, Object> test() {
        demo.forbidFor(CurrentUser.id(), "La cuenta de demostración de administrador no puede enviar correos.");
        User admin = userRepository.findById(CurrentUser.id()).orElseThrow(() -> BusinessException.notFound("Usuario no encontrado."));
        try {
            mailService.sendTest(admin.getEmail());
        } catch (MailDeliveryException ex) {
            auditService.recordIndependently(admin.getId(), "MAIL_TEST_FAILED", ex.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el correo de prueba: " + ex.getMessage());
        }
        auditService.record(admin.getId(), "MAIL_TEST_SENT", mailService.status().channel());
        return Map.of("sent", true, "to", admin.getEmail(), "channel", mailService.status().channel());
    }
}
