package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.xg.platform.api.persistence.mybatisplus.entity.MessageEntity;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;

public final class MessagePersistenceConvertor {

    private MessagePersistenceConvertor() {
    }

    public static MessageRecord toRecord(MessageEntity entity) {
        return new MessageRecord(
                entity.getMessageId(),
                entity.getThreadId(),
                MessageRole.valueOf(entity.getRole()),
                entity.getContent(),
                InteractionMode.valueOf(entity.getInteractionMode()),
                entity.getRunId(),
                entity.getTaskId(),
                entity.getCreatedAt()
        );
    }

    public static MessageEntity toEntity(String userId, MessageRecord record) {
        MessageEntity entity = new MessageEntity();
        entity.setMessageId(record.messageId());
        entity.setUserId(userId);
        entity.setThreadId(record.threadId());
        entity.setRole(record.role().name());
        entity.setContent(record.content());
        entity.setInteractionMode(record.interactionMode().name());
        entity.setRunId(record.runId());
        entity.setTaskId(record.taskId());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }
}
