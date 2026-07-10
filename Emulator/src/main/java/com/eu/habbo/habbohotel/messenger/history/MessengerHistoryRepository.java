package com.eu.habbo.habbohotel.messenger.history;

import java.util.List;

public interface MessengerHistoryRepository {
    boolean isActiveMember(long conversationId, int userId);

    List<MessengerStoredMessage> loadHistory(long conversationId, int userId, long beforeMessageId, int limit);

    void cleanupRetention(int days, int maxMessagesPerConversation);
}
