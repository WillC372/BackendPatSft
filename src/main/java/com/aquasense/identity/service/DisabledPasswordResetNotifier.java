package com.aquasense.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aquasense.mail.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(DisabledPasswordResetNotifier.class);

    @Override
    public void send(String email, String displayName, String resetCode, long validMinutes) {
        LOG.warn("SMTP no configurado (aquasense.mail.enabled=false): no se envió el correo de recuperación.");
    }
}
