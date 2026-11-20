package com.aquaassistant.notify;

import com.aquaassistant.user.MailService;
import com.aquaassistant.user.User;
import com.aquaassistant.user.UserRepository;
import java.util.Locale;

/** Concrete implementor: by email, only to active accounts whose address can receive mail. */
public class EmailChannel implements NoticeChannel {

    private final MailService mail;
    private final UserRepository users;

    public EmailChannel(MailService mail, UserRepository users) {
        this.mail = mail;
        this.users = users;
    }

    @Override
    public boolean deliver(Long userId, NoticeContent content) {
        return users.findById(userId).filter(User::isEnabled).filter(u -> deliverable(u.getEmail()))
            .map(u -> mail.sendNotice(u.getEmail(), content.emailSubject(), content.title(), content.emailParagraphs()))
            .orElse(false);
    }

    /**
     * Whether an email to this address can arrive: the demonstration accounts used made-up addresses
     * (@demo.aquaassistant) and the example accounts use @example.com, which do not receive mail.
     */
    public static boolean deliverable(String email) {
        String e = String.valueOf(email).toLowerCase(Locale.ROOT);
        return e.contains("@") && !e.endsWith(".aquaassistant") && !e.endsWith("@example.com") && !e.endsWith(".test")
            && !e.endsWith(".local");
    }
}
