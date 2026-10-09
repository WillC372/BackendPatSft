package com.poolguard.patterns.factory;

/**
 * visual alert: banner / color change in the user interface.
 */
public class VisualAlert implements Alert {

    @Override
    public String getType() {
        return "VISUAL";
    }

    @Override
    public void trigger(String messageEs, String severity) {
        System.out.println("[VISUAL][" + severity + "] " + messageEs);
    }
}
