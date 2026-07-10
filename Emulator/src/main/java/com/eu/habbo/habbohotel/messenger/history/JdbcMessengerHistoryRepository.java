package com.eu.habbo.habbohotel.messenger.history;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcMessengerHistoryRepository implements MessengerHistoryRepository {
    private static final int CLEANUP_BATCH_SIZE = 1000;

    private final DataSource dataSource;

    public JdbcMessengerHistoryRepository(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource is required");
        this.dataSource = dataSource;
    }

    @Override
    public boolean isActiveMember(long conversationId, int userId) {
        String sql = "SELECT 1 FROM messenger_members WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, conversationId);
            statement.setInt(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to validate messenger membership", exception);
        }
    }

    @Override
    public List<MessengerStoredMessage> loadHistory(long conversationId, int userId, long beforeMessageId, int limit) {
        String sql = """
                SELECT m.id, m.conversation_id, m.sender_id, m.type, m.message, m.metadata,
                       UNIX_TIMESTAMP(m.created_at) AS created_at
                FROM messenger_messages m
                JOIN messenger_members member
                  ON member.conversation_id = m.conversation_id AND member.user_id = ?
                WHERE m.conversation_id = ?
                  AND (? = 0 OR m.id < ?)
                  AND (member.joined_message_id IS NULL OR m.id >= member.joined_message_id)
                  AND (member.left_message_id IS NULL OR m.id <= member.left_message_id)
                ORDER BY m.id DESC
                LIMIT ?
                """;
        List<MessengerStoredMessage> messages = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setLong(2, conversationId);
            statement.setLong(3, beforeMessageId);
            statement.setLong(4, beforeMessageId);
            statement.setInt(5, limit + 1);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new MessengerStoredMessage(
                            result.getLong("id"),
                            result.getLong("conversation_id"),
                            result.getInt("sender_id"),
                            result.getInt("type"),
                            result.getString("message"),
                            result.getString("metadata"),
                            result.getLong("created_at")
                    ));
                }
            }
            return messages;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load messenger history", exception);
        }
    }

    @Override
    public void cleanupRetention(int days, int maxMessagesPerConversation) {
        String deleteExpired = "DELETE FROM messenger_messages WHERE created_at < UTC_TIMESTAMP() - INTERVAL ? DAY LIMIT ?";
        String deleteOverflow = """
                DELETE FROM messenger_messages
                WHERE id IN (
                    SELECT id FROM (
                        SELECT id, ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY id DESC) AS row_number
                        FROM messenger_messages
                    ) ranked
                    WHERE ranked.row_number > ?
                    LIMIT ?
                )
                """;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(deleteExpired)) {
                statement.setInt(1, days);
                statement.setInt(2, CLEANUP_BATCH_SIZE);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(deleteOverflow)) {
                statement.setInt(1, maxMessagesPerConversation);
                statement.setInt(2, CLEANUP_BATCH_SIZE);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clean messenger history", exception);
        }
    }
}
