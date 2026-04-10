package com.xg.platform.research.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.research.ResearchFindingRecord;
import com.xg.platform.contracts.research.ResearchIterationRecord;
import com.xg.platform.contracts.research.ResearchReportBlock;
import com.xg.platform.contracts.research.ResearchReportSection;
import com.xg.platform.contracts.research.ResearchReportView;
import com.xg.platform.contracts.research.ResearchSourceRecord;
import com.xg.platform.contracts.research.ResearchTaskSnapshotRecord;
import com.xg.platform.contracts.shared.task.TaskRecord;
import com.xg.platform.contracts.shared.task.TaskStatus;
import com.xg.platform.research.port.ResearchTaskSnapshotRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.WorkspaceManager;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

public class ResearchReadService {

    private static final TypeReference<List<ResearchReportSection>> PLAN_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<ResearchIterationRecord>> ITERATION_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<ResearchFindingRecord>> FINDING_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<ResearchSourceRecord>> SOURCE_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<ReportCitation>> CITATION_TYPE = new TypeReference<>() { };

    private final ThreadService threadRuntimeService;
    private final TaskRepository taskRepository;
    private final ResearchTaskSnapshotRepository researchTaskSnapshotRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;

    public ResearchReadService(ThreadService threadRuntimeService,
                               TaskRepository taskRepository,
                               ResearchTaskSnapshotRepository researchTaskSnapshotRepository,
                               WorkspaceManager workspaceManager,
                               ObjectMapper objectMapper) {
        this.threadRuntimeService = threadRuntimeService;
        this.taskRepository = taskRepository;
        this.researchTaskSnapshotRepository = researchTaskSnapshotRepository;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
    }

    public ResearchReportView getReport(String userId, String threadId, String taskId) {
        requireTask(userId, threadId, taskId);
        String markdown = readString(userId, threadId, taskId, "response.md");
        List<ReportCitation> citations = getCitations(userId, threadId, taskId);
        return new ResearchReportView(markdown, buildBlocks(markdown, citations));
    }

    public List<ResearchReportSection> getPlan(String userId, String threadId, String taskId) {
        ResearchTaskSnapshotRecord snapshot = readLiveSnapshot(userId, threadId, taskId);
        if (snapshot != null && !snapshot.plan().isEmpty()) {
            return snapshot.plan();
        }
        return readJson(userId, threadId, taskId, "response.plan.json", PLAN_TYPE);
    }

    public List<ResearchIterationRecord> getIterations(String userId, String threadId, String taskId) {
        ResearchTaskSnapshotRecord snapshot = readLiveSnapshot(userId, threadId, taskId);
        if (snapshot != null && !snapshot.iterations().isEmpty()) {
            return snapshot.iterations();
        }
        return readJson(userId, threadId, taskId, "response.iterations.json", ITERATION_TYPE);
    }

    public List<ResearchFindingRecord> getFindings(String userId, String threadId, String taskId) {
        ResearchTaskSnapshotRecord snapshot = readLiveSnapshot(userId, threadId, taskId);
        if (snapshot != null && !snapshot.findings().isEmpty()) {
            return snapshot.findings();
        }
        return readJson(userId, threadId, taskId, "response.findings.json", FINDING_TYPE);
    }

    public List<ResearchSourceRecord> getSources(String userId, String threadId, String taskId) {
        ResearchTaskSnapshotRecord snapshot = readLiveSnapshot(userId, threadId, taskId);
        if (snapshot != null && !snapshot.sources().isEmpty()) {
            return snapshot.sources();
        }
        return readJson(userId, threadId, taskId, "response.sources.json", SOURCE_TYPE);
    }

    public List<ReportCitation> getCitations(String userId, String threadId, String taskId) {
        ResearchTaskSnapshotRecord snapshot = readLiveSnapshot(userId, threadId, taskId);
        if (snapshot != null && !snapshot.citations().isEmpty()) {
            return snapshot.citations();
        }
        return readJson(userId, threadId, taskId, "response.citations.json", CITATION_TYPE);
    }

    private TaskRecord requireTask(String userId, String threadId, String taskId) {
        threadRuntimeService.getThread(userId, threadId);
        return taskRepository.findTask(userId, threadId, taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
    }

    private ResearchTaskSnapshotRecord readLiveSnapshot(String userId, String threadId, String taskId) {
        TaskRecord task = requireTask(userId, threadId, taskId);
        if (task.status() == TaskStatus.COMPLETED
                || task.status() == TaskStatus.FAILED
                || task.status() == TaskStatus.CANCELLED) {
            return null;
        }
        return researchTaskSnapshotRepository.findByTask(userId, threadId, taskId).orElse(null);
    }

    private Path resolveOutputPath(String userId, String threadId, String taskId, String fileName) {
        requireTask(userId, threadId, taskId);
        return workspaceManager.resolvePath(userId, threadId, WorkspaceArea.OUTPUTS, taskId + "/" + fileName);
    }

    private String readString(String userId, String threadId, String taskId, String fileName) {
        Path path = resolveOutputPath(userId, threadId, taskId, fileName);
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read research artifact: " + fileName, exception);
        }
    }

    private <T> T readJson(String userId, String threadId, String taskId, String fileName, TypeReference<T> typeReference) {
        Path path = resolveOutputPath(userId, threadId, taskId, fileName);
        try {
            return objectMapper.readValue(path.toFile(), typeReference);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read research artifact: " + fileName, exception);
        }
    }

    private List<ResearchReportBlock> buildBlocks(String markdown, List<ReportCitation> citations) {
        String[] paragraphs = markdown.split("\\R\\R+");
        java.util.ArrayList<ResearchReportBlock> blocks = new java.util.ArrayList<>();
        int index = 0;
        for (String paragraph : paragraphs) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                continue;
            }
            String blockId = "block-" + (++index);
            String paragraphId = "paragraph-" + index;
            List<String> citationIds = citations.stream()
                    .filter(citation -> citation.citationLabel() != null && normalized.contains("[" + citation.citationLabel() + "]"))
                    .map(ReportCitation::citationId)
                    .distinct()
                    .toList();
            blocks.add(new ResearchReportBlock(blockId, paragraphId, normalized, citationIds));
        }
        return List.copyOf(blocks);
    }
}
