package com.aquasense.admin;

import com.aquasense.user.MailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: emails the user when an administrator changes their plan, role or account status.
 * It listens after the commit: if the change is rolled back nothing is sent, and an email failure does not undo the change.
 * Without a configured email service, the notice is skipped.
 */
@Component
public class AccountNoticeListener {

    private final MailService mailService;

    public AccountNoticeListener(MailService mailService) {
        this.mailService = mailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotice(AccountNotice notice) {
        mailService.sendNotice(notice.email(), notice.subject(), notice.title(), notice.paragraphs());
    }
}
