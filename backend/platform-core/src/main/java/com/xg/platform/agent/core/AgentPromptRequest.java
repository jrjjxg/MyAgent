package com.xg.platform.agent.core;

import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.ThreadFileReference;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import com.xg.platform.memory.RetrievedChunk;
import com.xg.platform.tools.SkillDefinition;
import com.xg.platform.tools.ToolDescriptor;

import java.util.List;

public record AgentPromptRequest(
        String agentName,
        String message,
        ChatRouteKind routeKind,
        List<SkillDefinition> selectedSkills,
        List<ToolDescriptor> availableTools,
        List<MessageRecord> recentMessages,
        List<MessageRecord> pendingHistoricalMessages,
        List<ThreadFileReference> uploadedFiles,
        List<ArtifactRecord> artifacts,
        List<DocumentRecord> documents,
        List<RetrievedChunk> retrievedChunks,
        String sessionSummary,
        String longTermMemory,
        String currentPhase,
        List<String> executedActions,
        String workingMemory,
        List<String> selectedDocumentIds,
        String readingPlan,
        List<String> searchHints,
        List<SkillDescriptor> availableSkills,
        List<SkillDefinition> recommendedSkills,
        List<SkillDefinition> loadedSkills
) {
}
