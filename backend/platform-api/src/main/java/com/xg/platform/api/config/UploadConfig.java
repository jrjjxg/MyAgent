package com.xg.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.api.upload.ChunkUploadStateStore;
import com.xg.platform.api.upload.ChunkedUploadService;
import com.xg.platform.api.upload.InMemoryChunkUploadStateStore;
import com.xg.platform.api.upload.RedisChunkUploadStateStore;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.runtime.WorkspaceRuntimeService;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.UploadService;
import com.xg.platform.workspace.WorkspaceManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class UploadConfig {

    @Bean
    ChunkUploadStateStore chunkUploadStateStore(PlatformProperties properties,
                                                ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                                ObjectMapper objectMapper) {
        if (properties.getUpload().isRedisStateEnabled() && stringRedisTemplateProvider.getIfAvailable() != null) {
            return new RedisChunkUploadStateStore(
                    stringRedisTemplateProvider.getObject(),
                    objectMapper.copy(),
                    Duration.ofHours(properties.getUpload().getSessionTtlHours())
            );
        }
        return new InMemoryChunkUploadStateStore();
    }

    @Bean
    ChunkedUploadService chunkedUploadService(ThreadRuntimeService threadRuntimeService,
                                             WorkspaceRuntimeService workspaceRuntimeService,
                                             WorkspaceManager workspaceManager,
                                             UploadService uploadService,
                                             ArtifactService artifactService,
                                             DocumentIngestService documentIngestService,
                                             ChunkUploadStateStore chunkUploadStateStore,
                                             PlatformProperties properties) {
        return new ChunkedUploadService(
                threadRuntimeService,
                workspaceRuntimeService,
                workspaceManager,
                uploadService,
                artifactService,
                documentIngestService,
                chunkUploadStateStore,
                properties.getUpload().getMaxChunkSizeBytes()
        );
    }
}
