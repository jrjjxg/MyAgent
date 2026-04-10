package com.xg.platform.api.web.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.research.application.ResearchCommandService;
import com.xg.platform.contracts.research.ResearchDraftRecord;
import com.xg.platform.contracts.research.StartResearchRequest;
import com.xg.platform.contracts.research.UpdateResearchDraftRequest;
import com.xg.platform.contracts.shared.task.TaskRecord;
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

    private final ResearchCommandService researchCommandService;

    public ResearchController(ResearchCommandService researchCommandService) {
        this.researchCommandService = researchCommandService;
    }

    @GetMapping
    public ResponseEntity<ResearchDraftRecord> getDraft(@CurrentUserId String userId,
                                                        @PathVariable String threadId) {
        ResearchDraftRecord draft = researchCommandService.getActiveDraft(userId, threadId);
        return draft == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(draft);
    }

    @PostMapping("/start")
    public TaskRecord startResearch(@CurrentUserId String userId,
                                    @PathVariable String threadId,
                                    @RequestBody(required = false) StartResearchRequest request) {
        return researchCommandService.startResearch(userId, threadId, request);
    }

    @PutMapping
    public ResearchDraftRecord updateDraft(@CurrentUserId String userId,
                                           @PathVariable String threadId,
                                           @RequestBody UpdateResearchDraftRequest request) {
        return researchCommandService.updateResearchDraft(userId, threadId, request);
    }

    @DeleteMapping
    public void discardDraft(@CurrentUserId String userId,
                             @PathVariable String threadId) {
        researchCommandService.discardDraft(userId, threadId);
    }
}
