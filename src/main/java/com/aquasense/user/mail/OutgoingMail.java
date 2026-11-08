package com.aquasense.user.mail;

/** Email ready to send, with a text and an HTML version. */
public record OutgoingMail(String to, String subject, String text, String html) {
}
