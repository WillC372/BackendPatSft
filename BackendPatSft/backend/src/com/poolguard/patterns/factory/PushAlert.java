package com.poolguard.patterns.factory;

/**
 * Push notification alert: in-app notification center entry.
 */
public class PushAlert implements Alert {

    @Override
    public String getType() {
        return "PUSH";
    }

    @Override
    public void trigger(String messageEs, String severity) {
        System.out.println("[PUSH][" + severity + "] " + messageEs);
    }
}
