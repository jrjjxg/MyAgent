package com.xg.platform.api.controller;

import com.xg.platform.agent.core.AgentExecutionService;
import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.message.ResearchDraftRecord;
import com.xg.platform.contracts.message.StartResearchRequest;
import com.xg.platform.contracts.message.UpdateResearchDraftRequest;
import com.xg.platform.contracts.task.TaskRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/threads/{threadId}/research-draft")
public class ResearchController {

    private final AgentExecutionService agentExecutionService;

    public ResearchController(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    @GetMapping
    public ResponseEntity<ResearchDraftRecord> getDraft(@CurrentUserId String userId,
                                                        @PathVariable String threadId) {
        ResearchDraftRecord draft = agentExecutionService.getActiveDraft(userId, threadId);
        return draft == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(draft);
    }

    @PostMapping("/start")
    public TaskRecord startResearch(@CurrentUserId String userId,
                                    @PathVariable String threadId,
                                    @RequestBody(required = false) StartResearchRequest request) {
        return agentExecutionService.startResearch(userId, threadId, request);
    }

    @PutMapping
    public ResearchDraftRecord updateDraft(@CurrentUserId String userId,
                                           @PathVariable String threadId,
                                           @RequestBody UpdateResearchDraftRequest request) {
        return agentExecutionService.updateResearchDraft(userId, threadId, request);
    }

    @DeleteMapping
    public void discardDraft(@CurrentUserId String userId,
                             @PathVariable String threadId) {
        agentExecutionService.discardDraft(userId, threadId);
    }
}
