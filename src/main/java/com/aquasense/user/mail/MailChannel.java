package com.aquasense.user.mail;

/**
 * Strategy: an email delivery channel (Mailjet or Brevo HTTP API, or SMTP). The mail service picks the channel from the
 * configuration, without depending on how each one delivers.
 */
public interface MailChannel {

    /** Identifier used in the configuration: "mailjet", "brevo" or "smtp". */
    String id();

    String label();

    boolean isConfigured();

    void send(OutgoingMail mail, String fromEmail, String fromName);
}
