package com.aquaassistant.i18n;

import com.aquaassistant.user.UserRepository;
import com.aquaassistant.user.mail.OutgoingMail;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Writes each email in the language of the person who receives it. The subject, the paragraphs of the text version and
 * the texts between the tags of the HTML version are translated; links, addresses and the HTML itself stay the same.
 * If the translation is not available at that moment, the email goes in Spanish rather than not going at all.
 */
@Component
public class MailLocalizer {

    private static final Pattern HTML_TEXT = Pattern.compile(">([^<>]+)<");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern URL = Pattern.compile("^\\s*https?://\\S+\\s*$");

    private final UserRepository users;
    private final UserLanguage language;
    private final TranslationClient translations;

    public MailLocalizer(UserRepository users, UserLanguage language, TranslationClient translations) {
        this.users = users;
        this.language = language;
        this.translations = translations;
    }

    public OutgoingMail localize(OutgoingMail mail) {
        String lang = users.findByEmailIgnoreCase(mail.to()).map(u -> language.of(u.getId())).orElse(Languages.ORIGINAL);
        if (Languages.isOriginal(lang)) {
            return mail;
        }
        try {
            return translate(mail, lang);
        } catch (RuntimeException ex) {
            return mail;
        }
    }

    OutgoingMail translate(OutgoingMail mail, String lang) {
        Set<String> texts = new LinkedHashSet<>();
        texts.add(mail.subject());
        List<String> paragraphs = mail.text() == null ? List.of() : List.of(mail.text().split("\n"));
        paragraphs.stream().filter(MailLocalizer::translatable).forEach(p -> texts.add(p.trim()));
        List<String> htmlTexts = new ArrayList<>();
        if (mail.html() != null) {
            Matcher m = HTML_TEXT.matcher(mail.html());
            while (m.find()) {
                String raw = HtmlUtils.htmlUnescape(m.group(1));
                if (translatable(raw)) {
                    htmlTexts.add(raw.trim());
                    texts.add(raw.trim());
                }
            }
        }
        List<String> sources = new ArrayList<>(texts);
        List<String> targets = translations.translateAll(lang, sources);
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            map.put(sources.get(i), targets.get(i));
        }
        StringBuilder text = new StringBuilder();
        for (String line : paragraphs) {
            String trimmed = line.trim();
            text.append(translatable(line) ? line.replace(trimmed, map.getOrDefault(trimmed, trimmed)) : line).append('\n');
        }
        String html = mail.html();
        if (html != null) {
            Matcher m = HTML_TEXT.matcher(html);
            StringBuilder out = new StringBuilder();
            while (m.find()) {
                String raw = HtmlUtils.htmlUnescape(m.group(1));
                String replacement = m.group(0);
                if (translatable(raw)) {
                    String trimmed = raw.trim();
                    String translated = raw.replace(trimmed, map.getOrDefault(trimmed, trimmed));
                    replacement = ">" + HtmlUtils.htmlEscape(translated) + "<";
                }
                m.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(out);
            html = out.toString().replaceFirst("<html lang=\"es\">", "<html lang=\"" + lang + "\">");
        }
        return new OutgoingMail(mail.to(), map.getOrDefault(mail.subject(), mail.subject()), text.toString().stripTrailing(), html);
    }

    private static boolean translatable(String text) {
        return text != null && !text.isBlank() && LETTER.matcher(text).find() && !URL.matcher(text).matches();
    }
}
