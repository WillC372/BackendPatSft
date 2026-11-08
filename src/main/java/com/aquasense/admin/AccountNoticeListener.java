package com.aquasense.admin;

import com.aquasense.user.MailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: avisa por correo al usuario cuando un administrador cambia su plan, su rol o el estado de su cuenta.
 * Escucha después del commit: si el cambio se revierte no se envía nada, y un fallo del correo no deshace el cambio.
 * Sin servicio de correo configurado, el aviso se omite.
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
