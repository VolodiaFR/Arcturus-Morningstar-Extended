package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts Arcturus' wide {@code permissions} table into Polaris'
 * {@code permission_ranks} and {@code permission_definitions} representation.
 *
 * <p>This is a Java migration because the legacy permission columns are dynamic:
 * plugins and hotel owners may have added their own {@code cmd_*}/{@code acc_*}
 * columns. Every such column is preserved. If an existing Polaris hotel already
 * has populated normalized tables, they are treated as canonical and are not
 * overwritten from the stale compatibility table.
 */
public final class V5__normalize_legacy_permissions extends BaseJavaMigration {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> METADATA_COLUMNS = Set.of(
            "id", "rank_name", "hidden_rank", "badge", "job_description",
            "staff_color", "staff_background", "level", "room_effect", "log_commands",
            "prefix", "prefix_color", "auto_credits_amount", "auto_pixels_amount",
            "auto_gotw_amount", "auto_points_amount");

    @Override
    public boolean canExecuteInTransaction() {
        // MariaDB DDL commits implicitly. Flyway must not imply rollback safety.
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        int rankCount = rowCount(connection, "permission_ranks");
        int definitionCount = rowCount(connection, "permission_definitions");

        if (rankCount > 0 && definitionCount > 0) {
            ensureRankColumns(connection, rankIds(connection, "permission_ranks"));
            return;
        }

        if (rankCount != 0 || definitionCount != 0) {
            throw new FlywayException(
                    "Refusing to guess how to repair partially normalized permissions: "
                            + "permission_ranks has " + rankCount + " rows and permission_definitions has "
                            + definitionCount + " rows. Restore both tables from backup or empty both after inspection.");
        }

        ensureLegacyMetadataColumns(connection);
        List<Integer> ranks = rankIds(connection, "permissions");
        if (ranks.isEmpty()) {
            throw new FlywayException("The legacy permissions table has no ranks; Polaris cannot build its permission model.");
        }

        copyRankMetadata(connection);
        ensureRankColumns(connection, ranks);

        List<LegacyPermission> permissions = legacyPermissions(connection);
        if (permissions.isEmpty()) {
            throw new FlywayException("The legacy permissions table has no permission columns to migrate.");
        }

        insertDefinitions(connection, permissions);
        copyPermissionValues(connection, ranks, permissions);
    }

    private static void ensureLegacyMetadataColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE `permissions`
                      ADD COLUMN IF NOT EXISTS `hidden_rank` tinyint(1) NOT NULL DEFAULT 0 AFTER `rank_name`,
                      ADD COLUMN IF NOT EXISTS `job_description` varchar(255) NOT NULL DEFAULT 'Here to help' AFTER `badge`,
                      ADD COLUMN IF NOT EXISTS `staff_color` varchar(8) NOT NULL DEFAULT '#327fa8' AFTER `job_description`,
                      ADD COLUMN IF NOT EXISTS `staff_background` varchar(255) NOT NULL DEFAULT 'staff-bg.png' AFTER `staff_color`
                    """);
        }
    }

    private static void copyRankMetadata(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO `permission_ranks` (
                      `id`, `rank_name`, `hidden_rank`, `badge`, `job_description`,
                      `staff_color`, `staff_background`, `level`, `room_effect`, `log_commands`,
                      `prefix`, `prefix_color`, `auto_credits_amount`, `auto_pixels_amount`,
                      `auto_gotw_amount`, `auto_points_amount`
                    )
                    SELECT
                      `id`, `rank_name`, `hidden_rank`, `badge`, `job_description`,
                      `staff_color`, `staff_background`, `level`, `room_effect`, `log_commands`,
                      `prefix`, `prefix_color`, `auto_credits_amount`, `auto_pixels_amount`,
                      `auto_gotw_amount`, `auto_points_amount`
                    FROM `permissions`
                    """);
        }
    }

    private static void ensureRankColumns(Connection connection, List<Integer> ranks) throws SQLException {
        Set<String> existing = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COLUMN_NAME
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'permission_definitions'
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                existing.add(result.getString(1).toLowerCase(Locale.ROOT));
            }
        }

        try (Statement statement = connection.createStatement()) {
            for (int rank : ranks) {
                String column = "rank_" + rank;
                if (!existing.contains(column)) {
                    statement.execute("ALTER TABLE `permission_definitions` ADD COLUMN `" + column
                            + "` tinyint(3) unsigned NOT NULL DEFAULT 0");
                }
            }
        }
    }

    private static List<LegacyPermission> legacyPermissions(Connection connection) throws SQLException {
        List<LegacyPermission> permissions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'permissions'
                ORDER BY ORDINAL_POSITION
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String name = result.getString("COLUMN_NAME");
                if (METADATA_COLUMNS.contains(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                requireSafeIdentifier(name);
                String type = result.getString("COLUMN_TYPE");
                int maxValue = type != null && type.contains("'2'") ? 2 : 1;
                String comment = result.getString("COLUMN_COMMENT");
                if (comment == null || comment.isBlank()) {
                    comment = generatedComment(name, maxValue);
                }
                permissions.add(new LegacyPermission(name, maxValue, comment));
            }
        }
        return permissions;
    }

    private static void insertDefinitions(
            Connection connection,
            List<LegacyPermission> permissions) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`)
                VALUES (?, ?, ?)
                """)) {
            for (LegacyPermission permission : permissions) {
                statement.setString(1, permission.name());
                statement.setInt(2, permission.maxValue());
                statement.setString(3, permission.comment());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void copyPermissionValues(
            Connection connection,
            List<Integer> ranks,
            List<LegacyPermission> permissions) throws SQLException {
        for (LegacyPermission permission : permissions) {
            String permissionColumn = quoteIdentifier(permission.name());
            try (PreparedStatement read = connection.prepareStatement(
                    "SELECT COALESCE(" + permissionColumn + ", 0) FROM `permissions` WHERE `id` = ?")) {
                for (int rank : ranks) {
                    read.setInt(1, rank);
                    try (ResultSet result = read.executeQuery()) {
                        if (!result.next()) {
                            throw new FlywayException("Legacy permission rank " + rank + " disappeared during migration.");
                        }
                        try (PreparedStatement write = connection.prepareStatement(
                                "UPDATE `permission_definitions` SET `rank_" + rank
                                        + "` = ? WHERE `permission_key` = ?")) {
                            write.setInt(1, result.getInt(1));
                            write.setString(2, permission.name());
                            write.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private static List<Integer> rankIds(Connection connection, String table) throws SQLException {
        requireSafeIdentifier(table);
        List<Integer> ranks = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT `id` FROM `" + table + "` ORDER BY `id`")) {
            while (result.next()) {
                ranks.add(result.getInt(1));
            }
        }
        return ranks;
    }

    private static int rowCount(Connection connection, String table) throws SQLException {
        requireSafeIdentifier(table);
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static String generatedComment(String name, int maxValue) {
        String subject = name.length() > 4 ? name.substring(4).replace('_', ' ') : name;
        if (name.startsWith("cmd_")) {
            return "Controls access to the :" + subject + " command. Values: 0 = disabled, 1 = allowed"
                    + (maxValue == 2 ? ", 2 = allowed with room-owner rights." : ".");
        }
        if (name.startsWith("acc_")) {
            return "Controls the " + subject + " capability for this rank. Values: 0 = disabled, 1 = enabled"
                    + (maxValue == 2 ? ", 2 = enabled with room-owner rights." : ".");
        }
        return "Legacy permission value migrated from the Arcturus permissions table.";
    }

    private static String quoteIdentifier(String identifier) {
        requireSafeIdentifier(identifier);
        return "`" + identifier + "`";
    }

    private static void requireSafeIdentifier(String identifier) {
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new FlywayException("Unsafe identifier in legacy permissions schema: " + identifier);
        }
    }

    private record LegacyPermission(String name, int maxValue, String comment) {
    }
}
