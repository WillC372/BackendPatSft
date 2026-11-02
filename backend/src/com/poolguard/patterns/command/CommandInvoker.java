package com.poolguard.patterns.command;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * COMMAND PATTERN - Invoker.
 *
 * Executes commands and keeps an execution history (the "bitácora" of
 * actions performed through the application).
 */
public class CommandInvoker {

    private final Deque<Command> history = new ArrayDeque<>();

    /** Executes a command and records it in the history. */
    public void execute(Command command) {
        command.execute();
        history.push(command);
    }

    /** Returns the execution history (most recent first). */
    public Deque<Command> getHistory() {
        return history;
    }
}
