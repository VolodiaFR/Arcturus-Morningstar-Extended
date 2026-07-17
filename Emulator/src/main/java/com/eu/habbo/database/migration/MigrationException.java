package com.eu.habbo.database.migration;

/**
 * Thrown when preflight or migration fails. It is intentionally unchecked and
 * fatal: the caller at startup must let it propagate so the emulator never runs
 * against a half-upgraded or unrecognised schema.
 */
public class MigrationException extends RuntimeException {
    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
