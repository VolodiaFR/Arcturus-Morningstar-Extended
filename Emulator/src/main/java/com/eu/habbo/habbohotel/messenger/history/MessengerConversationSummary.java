package com.eu.habbo.habbohotel.messenger.history;

public record MessengerConversationSummary(
        long id,
        ConversationType type,
        String name,
        long lastMessageId,
        int unreadCount,
        long updatedAt
) {
}
