package com.aquasense.user.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** SMTP adapter (Spring Mail) for servers where the mail port is available. */
@Component
public class SmtpMailChannel implements MailChannel {

    private final ObjectProvider<JavaMailSender> sender;
    private final boolean configured;

    public SmtpMailChannel(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.configured = host != null && !host.isBlank();
    }

    @Override
    public String id() {
        return "smtp";
    }

    @Override
    public String label() {
        return "SMTP";
    }

    @Override
    public boolean isConfigured() {
        return configured && sender.getIfAvailable() != null;
    }

    @Override
    public void send(OutgoingMail mail, String fromEmail, String fromName) {
        JavaMailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
            throw new MailDeliveryException("SMTP no está disponible.");
        }
        try {
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            if (mail.html() != null) {
                helper.setText(mail.text(), mail.html());
            } else {
                helper.setText(mail.text());
            }
            mailSender.send(message);
        } catch (MailException | jakarta.mail.MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new MailDeliveryException("SMTP rechazó el envío: " + ex.getMessage(), ex);
        }
    }
}
