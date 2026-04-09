package com.xg.platform.api.persistence.mybatisplus.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.persistence.mybatisplus.convertor.MessagePersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.MessageEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.MessageMapper;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.runtime.MessageRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MybatisMessageRepository implements MessageRepository {

    private final MessageMapper messageMapper;

    public MybatisMessageRepository(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public MessageRecord append(String userId, MessageRecord messageRecord) {
        messageMapper.insert(MessagePersistenceConvertor.toEntity(userId, messageRecord));
        return messageRecord;
    }

    @Override
    public List<MessageRecord> listMessages(String userId, String threadId) {
        return messageMapper.selectList(
                        Wrappers.<MessageEntity>lambdaQuery()
                                .eq(MessageEntity::getUserId, userId)
                                .eq(MessageEntity::getThreadId, threadId)
                                .orderByAsc(MessageEntity::getCreatedAt))
                .stream()
                .map(MessagePersistenceConvertor::toRecord)
                .toList();
    }

    @Override
    public List<MessageRecord> listRecentMessages(String userId, String threadId, int limit) {
        List<MessageRecord> recentMessages = new ArrayList<>(messageMapper.selectList(
                        Wrappers.<MessageEntity>lambdaQuery()
                                .eq(MessageEntity::getUserId, userId)
                                .eq(MessageEntity::getThreadId, threadId)
                                .orderByDesc(MessageEntity::getCreatedAt)
                                .last("limit " + Math.max(1, limit)))
                .stream()
                .map(MessagePersistenceConvertor::toRecord)
                .toList());
        Collections.reverse(recentMessages);
        return List.copyOf(recentMessages);
    }

    @Override
    public Optional<String> findLatestMessageId(String userId, String threadId) {
        MessageEntity entity = messageMapper.selectOne(
                Wrappers.<MessageEntity>lambdaQuery()
                        .select(MessageEntity::getMessageId)
                        .eq(MessageEntity::getUserId, userId)
                        .eq(MessageEntity::getThreadId, threadId)
                        .orderByDesc(MessageEntity::getCreatedAt)
                        .last("limit 1")
        );
        return Optional.ofNullable(entity).map(MessageEntity::getMessageId);
    }

    @Override
    public Optional<MessageRecord> findById(String userId, String threadId, String messageId) {
        MessageEntity entity = messageMapper.selectOne(
                Wrappers.<MessageEntity>lambdaQuery()
                        .eq(MessageEntity::getUserId, userId)
                        .eq(MessageEntity::getThreadId, threadId)
                        .eq(MessageEntity::getMessageId, messageId)
        );
        return Optional.ofNullable(entity).map(MessagePersistenceConvertor::toRecord);
    }

    @Override
    public void deleteByThread(String userId, String threadId) {
        messageMapper.delete(
                Wrappers.<MessageEntity>lambdaQuery()
                        .eq(MessageEntity::getUserId, userId)
                        .eq(MessageEntity::getThreadId, threadId)
        );
    }
}
