package com.poolguard.patterns.command;

/**
 * COMMAND PATTERN
 *
 * Encapsulates every user action as an object. This allows queuing,
 * undoing, logging and, crucially for this project, feeding an immutable
 * audit trail in the database.
 */
public interface Command {

    /** Executes the action. */
    void execute();

    /** Human-readable description used by the audit log. */
    String getDescription();
}
