package com.aquasense.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aquasense.mail.enabled", havingValue = "true")
public class SmtpPasswordResetNotifier implements PasswordResetNotifier {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetNotifier(JavaMailSender mailSender, @Value("${aquasense.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String email, String displayName, String resetCode, long validMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("AquaSense AI: código para restablecer su contraseña");
        message.setText("Hola " + displayName + ",\n\n"
                + "Su código de recuperación es: " + resetCode + "\n"
                + "Es válido durante " + validMinutes + " minutos y solo puede usarse una vez.\n\n"
                + "Si usted no solicitó este cambio, ignore este mensaje.");
        mailSender.send(message);
    }
}
