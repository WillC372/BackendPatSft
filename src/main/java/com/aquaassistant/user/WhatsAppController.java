package com.aquaassistant.user;

import com.aquaassistant.audit.AuditService;
import com.aquaassistant.common.BusinessException;
import com.aquaassistant.notify.WhatsAppService;
import com.aquaassistant.security.CurrentUser;
import com.aquaassistant.security.RateLimiter;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The mobile number of the account and the alarms by WhatsApp: when the pH of the water is altered, besides the push
 * notice and the email, a WhatsApp message goes to this number (if the server has WhatsApp configured).
 */
@RestController
@RequestMapping("/api/v1/users/me/whatsapp")
public class WhatsAppController {

    public record WhatsAppDto(boolean configured, String phoneNumber, boolean enabled) {
    }

    public record WhatsAppRequest(String phoneNumber, Boolean enabled) {
    }

    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final WhatsAppService whatsApp;
    private final AuditService audit;
    private final RateLimiter rateLimiter;

    public WhatsAppController(UserRepository users, UserSettingsRepository settings, WhatsAppService whatsApp,
                              AuditService audit, RateLimiter rateLimiter) {
        this.users = users;
        this.settings = settings;
        this.whatsApp = whatsApp;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public WhatsAppDto get() {
        return dto(user(), settings.findById(CurrentUser.id()).orElse(null));
    }

    @PutMapping
    @Transactional
    public WhatsAppDto update(@RequestBody WhatsAppRequest request) {
        User user = user();
        String phone;
        try {
            phone = WhatsAppService.normalize(request.phoneNumber());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
        user.changePhoneNumber(phone);
        UserSettings s = settings.findById(user.getId()).orElse(null);
        if (s != null && request.enabled() != null) {
            s.setWhatsappAlerts(request.enabled());
        }
        audit.record(user.getId(), "PHONE_UPDATED", phone == null ? "Quitó su celular" : "Celular " + WhatsAppService.hint(phone));
        return dto(user, s);
    }

    /** Sends the alarm template to the account's number now, to check that it arrives. */
    @PostMapping("/test")
    @Transactional(readOnly = true)
    public WhatsAppService.Result test() {
        rateLimiter.check("whatsapp-test:" + CurrentUser.id());
        User user = user();
        if (user.getPhoneNumber() == null) {
            throw BusinessException.badRequest("Primero guarda tu número de celular.");
        }
        return whatsApp.send(user.getPhoneNumber(), "Prueba de Aqua Assistant AI",
            "Así te aviso por WhatsApp si el pH del agua se altera. Si recibiste este mensaje, ya está todo listo.");
    }

    private User user() {
        return users.findById(CurrentUser.id()).orElseThrow(() -> BusinessException.notFound("La cuenta no existe."));
    }

    private WhatsAppDto dto(User user, UserSettings s) {
        return new WhatsAppDto(whatsApp.configured(), user.getPhoneNumber(), s == null || s.isWhatsappAlerts());
    }
}
