package com.aquaassistant.notify;

import java.util.List;

/** Concrete decorator: the title and text go in the person's language (Spanish if the translation is not ready). */
public class TranslatedChannel extends ChannelDecorator {

    /** Translates texts into the language of a person, in the same order. */
    @FunctionalInterface
    public interface Translator {
        List<String> translate(Long userId, List<String> texts);
    }

    private final Translator translator;

    public TranslatedChannel(NoticeChannel inner, Translator translator) {
        super(inner);
        this.translator = translator;
    }

    @Override
    public boolean deliver(Long userId, NoticeContent content) {
        List<String> text = translator.translate(userId, List.of(content.title(), content.body()));
        return inner.deliver(userId, content.withText(text.get(0), text.get(1)));
    }
}
