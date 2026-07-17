package com.eu.habbo.database.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Inspects a database before migration and classifies it into one of the states
 * the adoption state machine handles. This is the safety gate that stops Polaris
 * from baselining and mutating a schema it does not recognise (for example a
 * mistyped database name).
 *
 * <p>Phase 2 ships the core classification (empty / already-managed / recognised
 * existing / unknown) using a minimal invariant set. Phase 4 refines the
 * recognised branch into pristine-Arc vs existing-pre-migration-Polaris and adds
 * per-object fingerprints; the enum and entry point are stable so that work slots
 * in without touching callers.
 */
public final class SchemaPreflight {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaPreflight.class);

    /** Tables that any Arc-family or Polaris database must have. Kept intentionally
     *  minimal so slightly-customised real installs are still recognised; extra
     *  tables are tolerated. */
    private static final Set<String> REQUIRED_INVARIANT_TABLES = new LinkedHashSet<>();

    static {
        REQUIRED_INVARIANT_TABLES.add("users");
        REQUIRED_INVARIANT_TABLES.add("items");
        REQUIRED_INVARIANT_TABLES.add("rooms");
        REQUIRED_INVARIANT_TABLES.add("emulator_settings");
    }

    public enum State {
        /** No user tables at all — a brand-new database. Apply V1 then V2..Vn. */
        EMPTY,
        /** Already has flyway_schema_history — validate and apply pending. */
        MANAGED,
        /** Non-empty, no Flyway history, but has the required invariant tables —
         *  a recognised Arc/Polaris install to baseline at V1 then migrate. */
        RECOGNISED_EXISTING,
        /** Non-empty, no Flyway history, missing required invariants — refuse. */
        UNKNOWN
    }

    private SchemaPreflight() {
    }

    public static State detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (tableExists(connection, "flyway_schema_history")) {
                return State.MANAGED;
            }

            int userTableCount = userTableCount(connection);
            if (userTableCount == 0) {
                return State.EMPTY;
            }

            Set<String> missing = new LinkedHashSet<>();
            for (String required : REQUIRED_INVARIANT_TABLES) {
                if (!tableExists(connection, required)) {
                    missing.add(required);
                }
            }

            if (missing.isEmpty()) {
                return State.RECOGNISED_EXISTING;
            }

            LOGGER.warn("[migrate] Database is non-empty but missing required invariant tables {} — refusing to touch it.", missing);
            return State.UNKNOWN;
        } catch (SQLException e) {
            throw new MigrationException("Could not inspect the database before migration", e);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static int userTableCount(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
