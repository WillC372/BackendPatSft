package com.aquaassistant.user;

import com.aquaassistant.audit.AuditService;
import com.aquaassistant.common.BusinessException;
import com.aquaassistant.notify.SmsService;
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
 * The mobile number of the account and the pH alarm by text message (SMS): when the pH of the water is altered,
 * besides the notice in the app, the push notice and the email, an SMS goes to this number (if the server has SMS on).
 */
@RestController
@RequestMapping("/api/v1/users/me/phone")
public class PhoneController {

    public record PhoneDto(boolean smsConfigured, String phoneNumber, boolean smsAlerts) {
    }

    public record PhoneRequest(String phoneNumber, Boolean smsAlerts) {
    }

    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final SmsService sms;
    private final AuditService audit;
    private final RateLimiter rateLimiter;

    public PhoneController(UserRepository users, UserSettingsRepository settings, SmsService sms, AuditService audit,
                           RateLimiter rateLimiter) {
        this.users = users;
        this.settings = settings;
        this.sms = sms;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PhoneDto get() {
        return dto(user(), settings.findById(CurrentUser.id()).orElse(null));
    }

    @PutMapping
    @Transactional
    public PhoneDto update(@RequestBody PhoneRequest request) {
        User user = user();
        String phone;
        try {
            phone = SmsService.normalize(request.phoneNumber());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
        user.changePhoneNumber(phone);
        UserSettings s = settings.findById(user.getId()).orElse(null);
        if (s != null && request.smsAlerts() != null) {
            s.setSmsAlerts(request.smsAlerts());
        }
        audit.record(user.getId(), "PHONE_UPDATED", phone == null ? "Quitó su celular" : "Celular " + SmsService.hint(phone));
        return dto(user, s);
    }

    /** Sends a test SMS to the account's number now, to check that it arrives (limited, as each SMS costs). */
    @PostMapping("/test")
    @Transactional(readOnly = true)
    public SmsService.Result test() {
        rateLimiter.check("sms-test:" + CurrentUser.id());
        User user = user();
        if (user.getPhoneNumber() == null) {
            throw BusinessException.badRequest("Primero guarda tu número de celular.");
        }
        return sms.send(user.getPhoneNumber(),
            "Aqua Assistant AI: así te aviso por SMS si el pH del agua se altera. Si recibiste este mensaje, ya está todo listo.");
    }

    private User user() {
        return users.findById(CurrentUser.id()).orElseThrow(() -> BusinessException.notFound("La cuenta no existe."));
    }

    private PhoneDto dto(User user, UserSettings s) {
        return new PhoneDto(sms.configured(), user.getPhoneNumber(), s == null || s.isSmsAlerts());
    }
}
