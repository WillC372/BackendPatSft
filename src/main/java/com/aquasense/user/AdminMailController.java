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
 * Estado del envío de correo y correo de prueba (rol ADMIN). La prueba solo se envía al correo del administrador
 * que la pide, así el endpoint no puede usarse para enviar correos a terceros.
 */
@RestController
@RequestMapping("/api/v1/admin/mail")
public class AdminMailController {

    private final MailService mailService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminMailController(MailService mailService, UserRepository userRepository, AuditService auditService) {
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
