package com.aquaassistant.notify;

import java.util.List;

/**
 * What a notice says, the same for every channel. {@code kind} groups notices of the same type (the alarm, an alert,
 * one reminder) so a channel can limit how often that type is repeated; {@code alarm} makes the notice urgent.
 */
public record NoticeContent(String kind, String title, String body, String url, String emailSubject,
                            List<String> emailParagraphs, boolean alarm) {

    public static NoticeContent of(String kind, Notifier.Message message) {
        return new NoticeContent(kind, message.title(), message.body(), message.url(), message.emailSubject(),
            message.emailParagraphs(), message.alarm());
    }

    /** The same notice with another title and text (a translation). */
    public NoticeContent withText(String newTitle, String newBody) {
        return new NoticeContent(kind, newTitle, newBody, url, emailSubject, emailParagraphs, alarm);
    }
}
