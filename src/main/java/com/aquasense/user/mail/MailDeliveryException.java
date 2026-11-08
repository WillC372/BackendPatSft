package com.aquasense.user.mail;

/** El canal no pudo entregar el correo. El mensaje nunca incluye el contenido del correo ni enlaces. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
