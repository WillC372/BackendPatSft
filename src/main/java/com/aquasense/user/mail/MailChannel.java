package com.aquasense.user.mail;

/**
 * Strategy: un canal de envío de correo (API HTTP de Brevo o SMTP). El servicio de correo elige el canal según la
 * configuración, sin depender de cómo entrega cada uno.
 */
public interface MailChannel {

    /** Identificador usado en la configuración: "brevo" o "smtp". */
    String id();

    String label();

    boolean isConfigured();

    void send(OutgoingMail mail, String fromEmail, String fromName);
}
