package com.eu.habbo.database.compat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariDataSource that routes every connection through the
 * {@link LegacySqlBridge}, so legacy plugin SQL is rewritten transparently.
 *
 * Extends HikariDataSource (instead of wrapping it) because
 * Database.getDataSource() must keep returning HikariDataSource — old plugin
 * jars were compiled against that exact signature.
 */
public class LegacyBridgeDataSource extends HikariDataSource {

    private final LegacySqlBridge bridge;

    public LegacyBridgeDataSource(HikariConfig configuration, LegacySqlBridge bridge) {
        super(configuration);
        this.bridge = bridge;
    }

    public LegacySqlBridge getBridge() {
        return this.bridge;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.bridge.wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return this.bridge.wrap(super.getConnection(username, password));
    }
}
