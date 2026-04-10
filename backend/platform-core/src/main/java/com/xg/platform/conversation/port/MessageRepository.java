package com.xg.platform.conversation.port;

import com.xg.platform.contracts.conversation.MessageRecord;

import java.util.List;
import java.util.Optional;

public interface MessageRepository {

    MessageRecord append(String userId, MessageRecord messageRecord);

    List<MessageRecord> listMessages(String userId, String threadId);

    default List<MessageRecord> listRecentMessages(String userId, String threadId, int limit) {
        List<MessageRecord> allMessages = listMessages(userId, threadId);
        int safeLimit = Math.max(1, limit);
        int fromIndex = Math.max(0, allMessages.size() - safeLimit);
        return List.copyOf(allMessages.subList(fromIndex, allMessages.size()));
    }

    default List<MessageRecord> listMessagesAfter(String userId, String threadId, String messageIdExclusive) {
        List<MessageRecord> allMessages = listMessages(userId, threadId);
        if (messageIdExclusive == null || messageIdExclusive.isBlank()) {
            return allMessages;
        }
        for (int index = 0; index < allMessages.size(); index++) {
            MessageRecord message = allMessages.get(index);
            if (messageIdExclusive.equals(message.messageId())) {
                return List.copyOf(allMessages.subList(index + 1, allMessages.size()));
            }
        }
        return allMessages;
    }

    Optional<String> findLatestMessageId(String userId, String threadId);

    Optional<MessageRecord> findById(String userId, String threadId, String messageId);

    void deleteByThread(String userId, String threadId);
}
