package com.aquasense.user.mail;

/** Correo listo para enviar, con versión de texto y HTML. */
public record OutgoingMail(String to, String subject, String text, String html) {
}
