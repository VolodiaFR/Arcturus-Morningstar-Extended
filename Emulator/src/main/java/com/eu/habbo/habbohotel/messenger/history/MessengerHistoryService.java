package com.eu.habbo.habbohotel.messenger.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessengerHistoryService {
    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final int DEFAULT_MAX_MESSAGES = 500;
    public static final int DEFAULT_PAGE_SIZE = 30;
    public static final int MAX_PAGE_SIZE = 50;

    private final MessengerHistoryRepository repository;
    private final int retentionDays;
    private final int maxMessagesPerConversation;

    public MessengerHistoryService(MessengerHistoryRepository repository) {
        this(repository, DEFAULT_RETENTION_DAYS, DEFAULT_MAX_MESSAGES);
    }

    public MessengerHistoryService(MessengerHistoryRepository repository, int retentionDays, int maxMessagesPerConversation) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        if (retentionDays <= 0) throw new IllegalArgumentException("retentionDays must be positive");
        if (maxMessagesPerConversation <= 0) throw new IllegalArgumentException("maxMessagesPerConversation must be positive");
        this.repository = repository;
        this.retentionDays = retentionDays;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
    }

    public MessengerHistoryPage loadHistory(long conversationId, int userId, long beforeMessageId, int requestedLimit) {
        if (conversationId <= 0 || userId <= 0) throw new IllegalArgumentException("conversationId and userId must be positive");
        if (!repository.isActiveMember(conversationId, userId)) throw new SecurityException("conversation access denied");

        int limit = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedLimit));
        List<MessengerStoredMessage> loaded = repository.loadHistory(conversationId, userId, Math.max(0, beforeMessageId), limit);
        boolean hasMore = loaded.size() > limit;
        List<MessengerStoredMessage> page = new ArrayList<>(hasMore ? loaded.subList(0, limit) : loaded);
        Collections.reverse(page);
        return new MessengerHistoryPage(page, hasMore);
    }

    public void cleanupRetention() {
        repository.cleanupRetention(retentionDays, maxMessagesPerConversation);
    }

    public static String directKey(int firstUserId, int secondUserId) {
        if (firstUserId <= 0 || secondUserId <= 0 || firstUserId == secondUserId) {
            throw new IllegalArgumentException("direct conversation requires two distinct positive user ids");
        }
        return Math.min(firstUserId, secondUserId) + ":" + Math.max(firstUserId, secondUserId);
    }
}
