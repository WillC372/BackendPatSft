package com.aquaassistant.notify;

import com.aquaassistant.user.User;
import com.aquaassistant.user.UserRepository;

/**
 * Concrete implementor of the Bridge: a text message (SMS) to the person's mobile number. It is a new way to deliver
 * that every kind of notice can use without changing; today only the pH alarm uses it, because each SMS costs money.
 */
public class SmsChannel implements NoticeChannel {

    private final SmsService sms;
    private final UserRepository users;

    public SmsChannel(SmsService sms, UserRepository users) {
        this.sms = sms;
        this.users = users;
    }

    @Override
    public boolean deliver(Long userId, NoticeContent content) {
        if (!sms.configured()) {
            return false;
        }
        return users.findById(userId).filter(User::isEnabled).map(User::getPhoneNumber)
            .map(phone -> sms.send(phone, "Aqua Assistant AI · " + content.title() + ": " + content.body()).sent())
            .orElse(false);
    }
}
