package com.xg.platform.workspace.application;

import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.workspace.ThreadRecord;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.WorkspaceManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.memory.port.LongTermMemoryJobRepository;
import com.xg.platform.memory.port.LongTermMemoryRepository;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.research.port.ResearchTaskSnapshotRepository;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.workspace.port.ThreadRepository;

public class ThreadDeletionService {

    private final ThreadService threadRuntimeService;
    private final ThreadRepository threadRepository;
    private final MessageRepository messageRepository;
    private final ResearchDraftRepository researchDraftRepository;
    private final TaskRepository taskRepository;
    private final RunEventRepository runEventRepository;
    private final ThreadMemorySnapshotRepository threadMemorySnapshotRepository;
    private final ResearchTaskSnapshotRepository researchTaskSnapshotRepository;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final LongTermMemoryJobRepository longTermMemoryJobRepository;
    private final DocumentStore documentStore;
    private final ArtifactService artifactService;
    private final WorkspaceManager workspaceManager;

    public ThreadDeletionService(ThreadService threadRuntimeService,
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
        this.threadRuntimeService = threadRuntimeService;
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.researchDraftRepository = researchDraftRepository;
        this.taskRepository = taskRepository;
        this.runEventRepository = runEventRepository;
        this.threadMemorySnapshotRepository = threadMemorySnapshotRepository;
        this.researchTaskSnapshotRepository = researchTaskSnapshotRepository;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.longTermMemoryJobRepository = longTermMemoryJobRepository;
        this.documentStore = documentStore;
        this.artifactService = artifactService;
        this.workspaceManager = workspaceManager;
    }

    public void deleteThread(String userId, String threadId) {
        ThreadRecord thread = threadRuntimeService.getThread(userId, threadId);
        String workspaceId = thread.workspaceId();

        List<DocumentRecord> deletedDocuments = documentStore.deleteBySourceThread(userId, workspaceId, threadId);
        Set<String> relatedArtifactIds = collectRelatedArtifactIds(deletedDocuments);
        artifactService.deleteArtifacts(
                userId,
                workspaceId,
                artifact -> threadId.equals(artifact.sourceThreadId()) || relatedArtifactIds.contains(artifact.artifactId())
        );
        for (DocumentRecord document : deletedDocuments) {
            workspaceManager.deleteWorkspacePath(
                    userId,
                    workspaceId,
                    WorkspaceArea.WORKSPACE,
                    "documents/" + document.documentId()
            );
        }

        longTermMemoryRepository.deleteBySourceThread(userId, threadId);
        longTermMemoryJobRepository.deleteByThread(userId, threadId);
        threadMemorySnapshotRepository.deleteByThread(userId, threadId);
        researchTaskSnapshotRepository.deleteByThread(userId, threadId);
        runEventRepository.deleteByThread(userId, threadId);
        researchDraftRepository.deleteByThread(userId, threadId);
        messageRepository.deleteByThread(userId, threadId);
        taskRepository.deleteByThread(userId, threadId);
        threadRepository.deleteThread(userId, threadId);
        workspaceManager.deleteThreadWorkspace(userId, threadId);
    }

    private Set<String> collectRelatedArtifactIds(List<DocumentRecord> documents) {
        Set<String> artifactIds = new LinkedHashSet<>();
        for (DocumentRecord document : documents) {
            addIfPresent(artifactIds, document.sourceArtifactId());
            addIfPresent(artifactIds, document.primaryTextArtifactId());
            addIfPresent(artifactIds, document.chunkIndexArtifactId());
        }
        return artifactIds;
    }

    private void addIfPresent(Set<String> values, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            values.add(candidate);
        }
    }
}
