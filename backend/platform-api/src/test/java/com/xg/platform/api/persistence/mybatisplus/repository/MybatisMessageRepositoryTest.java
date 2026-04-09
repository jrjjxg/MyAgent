package com.xg.platform.api.persistence.mybatisplus.repository;

import com.xg.platform.api.persistence.mybatisplus.entity.MessageEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.MessageMapper;
import com.xg.platform.contracts.message.MessageRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisMessageRepositoryTest {

    @Test
    void listRecentMessagesReturnsChronologicalOrderWithoutMutatingImmutableList() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        MybatisMessageRepository repository = new MybatisMessageRepository(messageMapper);

        MessageEntity newest = messageEntity("message-2", "thread-1", "assistant", "world", Instant.parse("2026-01-04T00:00:01Z"));
        MessageEntity oldest = messageEntity("message-1", "thread-1", "user", "hello", Instant.parse("2026-01-04T00:00:00Z"));
        when(messageMapper.selectList(any())).thenReturn(List.of(newest, oldest));

        assertThat(repository.listRecentMessages("user-1", "thread-1", 2))
                .extracting(record -> record.messageId(), record -> record.role(), record -> record.content())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("message-1", MessageRole.USER, "hello"),
                        org.assertj.core.groups.Tuple.tuple("message-2", MessageRole.ASSISTANT, "world")
                );
    }

    private static MessageEntity messageEntity(String messageId, String threadId, String role, String content, Instant createdAt) {
        MessageEntity entity = new MessageEntity();
        entity.setMessageId(messageId);
        entity.setUserId("user-1");
        entity.setThreadId(threadId);
        entity.setRole(role.toUpperCase());
        entity.setContent(content);
        entity.setInteractionMode("CHAT");
        entity.setRunId("run-1");
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
