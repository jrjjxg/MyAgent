package com.xg.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.LongTermMemoryMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.MemoryExtractionJobMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.MessageMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.ResearchDraftMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.ResearchTaskSnapshotMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.RunEventMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.SkillUserConfigMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.TaskMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.ThreadMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.ThreadMemorySnapshotMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.UserModelProviderConfigMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.UserWebSearchConfigMapper;
import com.xg.platform.api.persistence.mybatisplus.mapper.WorkspaceMapper;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisLongTermMemoryJobRepository;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisLongTermMemoryRepository;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisMessageRepository;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisResearchDraftRepository;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisResearchTaskSnapshotRepository;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisRunEventRepository;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisSkillConfigStore;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisWorkspaceRepository;
import com.xg.platform.api.model.ModelProviderConfigService;
import com.xg.platform.api.search.WebSearchConfigService;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisTaskRepository;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisThreadMemorySnapshotRepository;
import com.xg.platform.api.persistence.mybatisplus.repository.MybatisThreadRepository;
import com.xg.platform.api.skill.SkillSecretCrypto;
import com.xg.platform.shared.runtime.graph.CheckpointConfiguration;
import com.xg.platform.document.application.ChunkIndexStore;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.memory.port.LongTermMemoryJobRepository;
import com.xg.platform.memory.port.LongTermMemoryRepository;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.research.port.ResearchTaskSnapshotRepository;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;
import com.xg.platform.workspace.application.ThreadDeletionService;
import com.xg.platform.workspace.port.ThreadRepository;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.port.WorkspaceRepository;
import com.xg.platform.workspace.application.WorkspaceService;
import com.xg.platform.skill.port.SkillConfigStore;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.UploadService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfig {

    @Bean
    WorkspaceManager workspaceManager(PlatformProperties properties) {
        return new WorkspaceManager(properties.getResolvedDataRoot());
    }

    @Bean
    ArtifactService artifactService(WorkspaceManager workspaceManager,
                                    ThreadService threadRuntimeService,
                                    ObjectMapper objectMapper) {
        return new ArtifactService(workspaceManager, threadRuntimeService, objectMapper.copy());
    }

    @Bean
    UploadService uploadService(WorkspaceManager workspaceManager,
                                ArtifactService artifactService,
                                ThreadService threadRuntimeService) {
        return new UploadService(workspaceManager, artifactService, threadRuntimeService);
    }

    @Bean
    WorkspaceRepository workspaceRepository(WorkspaceMapper workspaceMapper) {
        return new MybatisWorkspaceRepository(workspaceMapper);
    }

    @Bean
    WorkspaceService workspaceRuntimeService(WorkspaceRepository workspaceRepository) {
        return new WorkspaceService(workspaceRepository);
    }

    @Bean
    ThreadRepository threadRepository(ThreadMapper threadMapper, WorkspaceRepository workspaceRepository) {
        return new MybatisThreadRepository(threadMapper, workspaceRepository);
    }

    @Bean
    ThreadService threadRuntimeService(ThreadRepository threadRepository) {
        return new ThreadService(threadRepository);
    }

    @Bean
    ThreadDeletionService threadDeletionService(ThreadService threadRuntimeService,
                                                ThreadRepository threadRepository,
                                                MessageRepository messageRepository,
                                                ResearchDraftRepository researchDraftRepository,
                                                TaskRepository taskRepository,
                                                RunEventRepository runEventRepository,
                                                ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                                ResearchTaskSnapshotRepository researchTaskSnapshotRepository,
                                                LongTermMemoryRepository longTermMemoryRepository,
                                                LongTermMemoryJobRepository longTermMemoryJobRepository,
                                                DocumentStore documentStore,
                                                ArtifactService artifactService,
                                                WorkspaceManager workspaceManager) {
        return new ThreadDeletionService(
                threadRuntimeService,
                threadRepository,
                messageRepository,
                researchDraftRepository,
                taskRepository,
                runEventRepository,
                threadMemorySnapshotRepository,
                researchTaskSnapshotRepository,
                longTermMemoryRepository,
                longTermMemoryJobRepository,
                documentStore,
                artifactService,
                workspaceManager
        );
    }

    @Bean
    MessageRepository messageRepository(MessageMapper messageMapper) {
        return new MybatisMessageRepository(messageMapper);
    }

    @Bean
    ResearchDraftRepository researchDraftRepository(ResearchDraftMapper researchDraftMapper, ObjectMapper objectMapper) {
        return new MybatisResearchDraftRepository(researchDraftMapper, objectMapper.copy());
    }

    @Bean
    TaskRepository taskRepository(TaskMapper taskMapper) {
        return new MybatisTaskRepository(taskMapper);
    }

    @Bean
    ResearchTaskSnapshotRepository researchTaskSnapshotRepository(ResearchTaskSnapshotMapper researchTaskSnapshotMapper,
                                                                 ObjectMapper objectMapper) {
        return new MybatisResearchTaskSnapshotRepository(researchTaskSnapshotMapper, objectMapper.copy());
    }

    @Bean
    RunEventRepository runEventRepository(RunEventMapper runEventMapper, ObjectMapper objectMapper) {
        return new MybatisRunEventRepository(runEventMapper, objectMapper.copy());
    }

    @Bean
    ThreadMemorySnapshotRepository threadMemorySnapshotRepository(ThreadMemorySnapshotMapper threadMemorySnapshotMapper,
                                                                 ObjectMapper objectMapper) {
        return new MybatisThreadMemorySnapshotRepository(threadMemorySnapshotMapper, objectMapper.copy());
    }

    @Bean
    LongTermMemoryRepository longTermMemoryRepository(LongTermMemoryMapper longTermMemoryMapper) {
        return new MybatisLongTermMemoryRepository(longTermMemoryMapper);
    }

    @Bean
    LongTermMemoryJobRepository longTermMemoryJobRepository(MemoryExtractionJobMapper memoryExtractionJobMapper) {
        return new MybatisLongTermMemoryJobRepository(memoryExtractionJobMapper);
    }

    @Bean
    SkillSecretCrypto skillSecretCrypto(PlatformProperties properties) {
        String secretEncryptionKey = properties.getSkills().getSecretEncryptionKey();
        if (secretEncryptionKey == null || secretEncryptionKey.isBlank()) {
            return null;
        }
        return new SkillSecretCrypto(secretEncryptionKey);
    }

    @Bean
    SkillConfigStore skillConfigStore(SkillUserConfigMapper skillUserConfigMapper,
                                      ObjectMapper objectMapper,
                                      ObjectProvider<SkillSecretCrypto> skillSecretCryptoProvider) {
        return new MybatisSkillConfigStore(
                skillUserConfigMapper,
                objectMapper.copy(),
                skillSecretCryptoProvider.getIfAvailable()
        );
    }

    @Bean
    ModelProviderConfigService modelProviderConfigService(UserModelProviderConfigMapper userModelProviderConfigMapper,
                                                          ObjectProvider<SkillSecretCrypto> skillSecretCryptoProvider,
                                                          PlatformProperties properties) {
        return new ModelProviderConfigService(
                userModelProviderConfigMapper,
                skillSecretCryptoProvider.getIfAvailable(),
                properties
        );
    }

    @Bean
    WebSearchConfigService webSearchConfigService(UserWebSearchConfigMapper userWebSearchConfigMapper,
                                                  ObjectProvider<SkillSecretCrypto> skillSecretCryptoProvider,
                                                  PlatformProperties properties) {
        return new WebSearchConfigService(
                userWebSearchConfigMapper,
                skillSecretCryptoProvider.getIfAvailable(),
                properties
        );
    }

    @Bean
    DocumentStore documentStore(WorkspaceManager workspaceManager,
                                ThreadService threadRuntimeService,
                                ObjectMapper objectMapper) {
        return new DocumentStore(workspaceManager, threadRuntimeService, objectMapper.copy());
    }

    @Bean
    ChunkIndexStore chunkIndexStore(WorkspaceManager workspaceManager, ObjectMapper objectMapper) {
        return new ChunkIndexStore(workspaceManager, objectMapper.copy());
    }

    @Bean
    CheckpointConfiguration checkpointConfiguration(DataSource dataSource,
                                                    ObjectMapper objectMapper) {
        return new CheckpointConfiguration(dataSource, objectMapper);
    }
}
