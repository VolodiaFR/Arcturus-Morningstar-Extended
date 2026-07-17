package com.eu.habbo.database.migration;

import java.util.Locale;

/**
 * Small command-line surface around Flyway. Normal startup remains automatic;
 * these options exist for deployment scripts and troubleshooting.
 */
public record MigrationOptions(Mode mode, boolean migrationsOnly) {

    public enum Mode {
        CONFIGURED,
        APPLY,
        VALIDATE
    }

    public static MigrationOptions parse(String[] arguments) {
        Mode mode = Mode.CONFIGURED;
        boolean migrationsOnly = false;

        for (String argument : arguments) {
            if ("--migrations-only".equals(argument)) {
                migrationsOnly = true;
                continue;
            }
            if (!argument.startsWith("--migrations=")) {
                continue;
            }

            String value = argument.substring("--migrations=".length())
                    .trim()
                    .toLowerCase(Locale.ROOT);
            mode = switch (value) {
                case "apply" -> Mode.APPLY;
                case "validate", "status" -> Mode.VALIDATE;
                case "off" -> throw new IllegalArgumentException(
                        "--migrations=off is intentionally not supported. "
                                + "Set db.migrate.on_startup=false explicitly in config.ini instead.");
                default -> throw new IllegalArgumentException(
                        "Unsupported migration mode '" + value + "'; expected apply or validate.");
            };
        }

        return new MigrationOptions(mode, migrationsOnly);
    }
}
