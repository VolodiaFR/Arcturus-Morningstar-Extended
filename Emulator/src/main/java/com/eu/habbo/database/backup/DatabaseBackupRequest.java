package com.eu.habbo.database.backup;

import com.eu.habbo.core.ConfigurationManager;

import java.util.Objects;

public record DatabaseBackupRequest(
        String host,
        int port,
        String database,
        String username,
        String password) {

    public DatabaseBackupRequest {
        host = safe(Objects.requireNonNull(host, "host"), "host");
        database = safe(Objects.requireNonNull(database, "database"), "database");
        username = safe(Objects.requireNonNull(username, "username"), "username");
        password = safe(Objects.requireNonNull(password, "password"), "password");
        if (host.isBlank() || database.isBlank() || username.isBlank()) {
            throw new IllegalArgumentException("database backup connection fields must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("database backup port must be between 1 and 65535");
        }
    }

    public static DatabaseBackupRequest resolve(ConfigurationManager config) {
        Objects.requireNonNull(config, "config");
        return new DatabaseBackupRequest(
                config.getValue("db.hostname", "localhost"),
                config.getInt("db.port", 3306),
                config.getValue("db.database"),
                config.getValue("db.username"),
                config.getValue("db.password"));
    }

    private static String safe(String value, String field) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " contains an unsafe control character");
        }
        return value;
    }
}
