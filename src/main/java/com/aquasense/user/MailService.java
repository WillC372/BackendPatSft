package com.aquasense.user;

import com.aquasense.config.AppProperties;
import com.aquasense.user.mail.MailChannel;
import com.aquasense.user.mail.MailDeliveryException;
import com.aquasense.user.mail.MailDeliveryProperties;
import com.aquasense.user.mail.OutgoingMail;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends the application emails through the configured channel (Mailjet or Brevo API, or SMTP).
 *
 * <p>Without a configured channel there are two behaviors:
 * <ul>
 *   <li>Production (default): recovery is not available and the link is never written to the log, because
 *       anyone with access to the logs could use it to take over an account.</li>
 *   <li>Development ({@code MAIL_LOG_RESET_LINKS=true}): the link is written to the log so the flow can be tested.
 *       A warning at startup says this mode is active.</li>
 * </ul>
 */
@Service
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private final AppProperties.Mail config;
    private final MailDeliveryProperties delivery;
    private final MailChannel channel;

    public MailService(List<MailChannel> channels, AppProperties properties, MailDeliveryProperties delivery) {
        this.config = properties.mail();
        this.delivery = delivery;
        this.channel = select(channels, delivery.provider()).orElse(null);
        if (channel != null) {
            LOG.info("Email delivery: {} (sender {}).", channel.label(), config.from());
            if (config.logResetLinks()) {
                LOG.info("An email channel is configured: MAIL_LOG_RESET_LINKS is ignored and reset links are only emailed.");
            }
        } else if (config.logResetLinks()) {
            LOG.warn("DEVELOPMENT MODE: MAIL_LOG_RESET_LINKS=true and no email channel is configured. Password reset links"
                + " will be written to this log. Never enable this in production.");
        } else {
            LOG.info("No email channel is configured: password recovery by email is disabled.");
        }
    }

    static Optional<MailChannel> select(List<MailChannel> channels, String provider) {
        if ("none".equals(provider)) {
            return Optional.empty();
        }
        List<String> order = "auto".equals(provider) ? List.of("mailjet", "brevo", "smtp") : List.of(provider);
        for (String id : order) {
            Optional<MailChannel> found = channels.stream().filter(c -> c.id().equals(id) && c.isConfigured()).findFirst();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** Tells whether email recovery can work (a channel, or the development mode that writes to the log). */
    public boolean isPasswordRecoveryAvailable() {
        return channel != null || config.logResetLinks();
    }

    public boolean isDevelopmentLogMode() {
        return channel == null && config.logResetLinks();
    }

    public Status status() {
        return new Status(channel != null, channel == null ? null : channel.label(), config.from(), delivery.senderName(),
            isDevelopmentLogMode());
    }

    public boolean sendPasswordReset(String email, String rawToken) {
        String link = config.frontendResetUrl() + "?token=" + rawToken;
        if (channel == null) {
            if (config.logResetLinks()) {
                LOG.warn("DEVELOPMENT ONLY - no email channel. Password reset link for {}: {}", email, link);
            } else {
                LOG.warn("Password reset requested but no email channel is configured; nothing was sent.");
            }
            return false;
        }
        return deliver(resetMail(email, link), "password reset");
    }

    /** Link to confirm that the owner of the ADMIN_EMAIL address is the one who registered. */
    public boolean sendAdminVerification(String email, String rawToken) {
        String link = config.frontendAdminVerifyUrl() + "?token=" + rawToken;
        if (channel == null) {
            if (config.logResetLinks()) {
                LOG.warn("DEVELOPMENT ONLY - no email channel. Admin verification link for {}: {}", email, link);
                return true;
            }
            LOG.warn("Admin verification requested but no email channel is configured; nothing was sent.");
            return false;
        }
        String text = """
            Alguien creó o inició sesión en una cuenta de AquaSense AI con este correo, configurado como administrador.

            Si fuiste tú, confirma el rol de administrador con este enlace (vence en 24 horas):
            %s

            Si no fuiste tú, ignora este mensaje: la cuenta no obtendrá permisos de administrador.
            """.formatted(link);
        String safe = HtmlUtils.htmlEscape(link);
        String html = layout("Confirma tu cuenta de administrador",
            "<p>Alguien creó o inició sesión en una cuenta de AquaSense AI con este correo, configurado como administrador.</p>"
                + "<p style=\"margin:28px 0\"><a href=\"" + safe + "\" style=\"background:#1e8bff;color:#ffffff;padding:13px 22px;"
                + "border-radius:999px;text-decoration:none;font-weight:700;display:inline-block\">Confirmar administrador</a></p>"
                + "<p style=\"color:#5b6b85;font-size:13px\">El enlace vence en 24 horas y solo se puede usar una vez. "
                + "Si no fuiste tú, ignora este mensaje: la cuenta no obtendrá permisos de administrador.</p>");
        return deliver(new OutgoingMail(email, "AquaSense AI - Confirma tu cuenta de administrador", text, html), "admin verification");
    }

    /** Simple account notice (approved payment, Premium about to expire). Without an email channel nothing is sent. */
    public boolean sendNotice(String to, String subject, String title, java.util.List<String> paragraphs) {
        if (channel == null) {
            return false;
        }
        java.util.List<String> parts = paragraphs.stream().filter(p -> p != null && !p.isBlank()).toList();
        String text = String.join("\n\n", parts);
        StringBuilder html = new StringBuilder();
        parts.forEach(p -> html.append("<p>").append(HtmlUtils.htmlEscape(p)).append("</p>"));
        return deliver(new OutgoingMail(to, subject, text, layout(title, html.toString())), "notice");
    }

    /** Test email so an administrator can check the configuration. */
    public void sendTest(String to) {
        if (channel == null) {
            throw new MailDeliveryException("No hay un canal de correo configurado.");
        }
        String text = """
            Este es un correo de prueba de AquaSense AI.

            Si lo recibiste, el envío de correos está configurado correctamente (%s).
            """.formatted(channel.label());
        channel.send(new OutgoingMail(to, "AquaSense AI - Correo de prueba", text, layout("Correo de prueba",
            "<p>Si recibiste este mensaje, el envío de correos de AquaSense AI está configurado correctamente.</p>"
                + "<p style=\"color:#5b6b85;font-size:13px\">Canal: " + HtmlUtils.htmlEscape(channel.label()) + "</p>")),
            config.from(), delivery.senderName());
    }

    private boolean deliver(OutgoingMail mail, String kind) {
        try {
            channel.send(mail, config.from(), delivery.senderName());
            LOG.info("Email ({}) sent via {}.", kind, channel.label());
            return true;
        } catch (MailDeliveryException ex) {
            LOG.error("Email ({}) could not be sent via {}: {}", kind, channel.label(), ex.getMessage());
            return false;
        }
    }

    static OutgoingMail resetMail(String to, String link) {
        String text = """
            Recibimos una solicitud para restablecer tu contraseña de AquaSense AI.

            Abre este enlace en los próximos 30 minutos:
            %s

            Si no la solicitaste, ignora este mensaje: tu contraseña no cambiará.
            """.formatted(link);
        String safe = HtmlUtils.htmlEscape(link);
        String html = layout("Restablecer tu contraseña",
            "<p>Recibimos una solicitud para restablecer tu contraseña de AquaSense AI.</p>"
                + "<p style=\"margin:28px 0\"><a href=\"" + safe + "\" style=\"background:#1e8bff;color:#ffffff;padding:13px 22px;"
                + "border-radius:999px;text-decoration:none;font-weight:700;display:inline-block\">Restablecer contraseña</a></p>"
                + "<p style=\"color:#5b6b85;font-size:13px\">El enlace vence en 30 minutos y solo se puede usar una vez. "
                + "Si el botón no funciona, copia esta dirección en el navegador:<br><span style=\"word-break:break-all\">" + safe
                + "</span></p><p style=\"color:#5b6b85;font-size:13px\">Si no la solicitaste, ignora este mensaje: tu contraseña no cambiará.</p>");
        return new OutgoingMail(to, "AquaSense AI - Restablecer contraseña", text, html);
    }

    private static String layout(String title, String content) {
        return "<!doctype html><html lang=\"es\"><body style=\"margin:0;background:#eaf3fc;font-family:Segoe UI,Arial,sans-serif;color:#0b1f3a\">"
            + "<div style=\"max-width:520px;margin:0 auto;padding:32px 20px\">"
            + "<div style=\"font-size:22px;font-weight:800;color:#0b1f3a\">AquaSense <span style=\"color:#1e8bff\">AI</span></div>"
            + "<div style=\"background:#ffffff;border-radius:16px;padding:28px;margin-top:16px;box-shadow:0 8px 24px rgba(11,31,58,.08)\">"
            + "<h1 style=\"font-size:20px;margin:0 0 12px\">" + HtmlUtils.htmlEscape(title) + "</h1>" + content + "</div>"
            + "<p style=\"color:#5b6b85;font-size:12px;text-align:center;margin-top:16px\">AquaSense AI · Monitoreo inteligente del pH del agua</p>"
            + "</div></body></html>";
    }

    public record Status(boolean configured, String channel, String from, String senderName, boolean developmentLogMode) {
    }
}
