package com.aquasense.admin;

import java.util.List;

/**
 * Event: an administrator changed something in a user's account. It is published inside the transaction and the email
 * is sent only if the change was saved (see {@link AccountNoticeListener}).
 */
public record AccountNotice(String email, String subject, String title, List<String> paragraphs) {
}
