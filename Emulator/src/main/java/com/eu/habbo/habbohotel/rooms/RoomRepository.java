package com.eu.habbo.habbohotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

final class RoomRepository {

    private static final String FIND_WIRED_SETTINGS_SQL =
            "SELECT inspect_mask, modify_mask FROM room_wired_settings "
                    + "WHERE room_id = ? LIMIT 1";
    private static final String UPDATE_USER_COUNT_SQL =
            "UPDATE rooms SET users = ? WHERE id = ? LIMIT 1";

    private final RoomDependencies.ConnectionProvider database;

    RoomRepository(RoomDependencies.ConnectionProvider database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    WiredSettings findWiredSettings(int roomId) throws SQLException {
        try (Connection connection = this.database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_WIRED_SETTINGS_SQL)) {
            statement.setInt(1, roomId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new WiredSettings(
                            resultSet.getInt("inspect_mask"),
                            resultSet.getInt("modify_mask"));
                }
            }
        }

        return WiredSettings.defaults();
    }

    void updateUserCount(int roomId, int userCount) throws SQLException {
        try (Connection connection = this.database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(UPDATE_USER_COUNT_SQL)) {
            statement.setInt(1, userCount);
            statement.setInt(2, roomId);
            statement.executeUpdate();
        }
    }

    record WiredSettings(int inspectMask, int modifyMask) {

        static WiredSettings defaults() {
            return new WiredSettings(
                    Room.WIRED_ACCESS_DEFAULT_INSPECT_MASK,
                    Room.WIRED_ACCESS_DEFAULT_MODIFY_MASK);
        }
    }
}
