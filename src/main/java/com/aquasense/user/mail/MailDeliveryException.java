package com.aquasense.user.mail;

/** The channel could not deliver the email. The message never includes the email content or links. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
