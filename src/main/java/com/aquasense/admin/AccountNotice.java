package com.aquasense.admin;

import java.util.List;

/**
 * Evento: un administrador cambió algo en la cuenta de un usuario. Se publica dentro de la transacción y el correo sale
 * solo si el cambio se guardó (ver {@link AccountNoticeListener}).
 */
public record AccountNotice(String email, String subject, String title, List<String> paragraphs) {
}
