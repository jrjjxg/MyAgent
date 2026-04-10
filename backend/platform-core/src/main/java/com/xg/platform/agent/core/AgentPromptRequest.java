package com.xg.platform.agent.core;

import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.conversation.domain.ConversationRouteKind;
import com.xg.platform.document.domain.RetrievedChunk;
import com.xg.platform.skill.domain.SkillDefinition;
import com.xg.platform.tooling.domain.ToolDescriptor;

import java.util.List;

public record AgentPromptRequest(
        String agentName,
        String message,
        ConversationRouteKind routeKind,
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
