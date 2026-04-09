package com.xg.platform.api.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.api.skill.SkillConfigService;
import com.xg.platform.contracts.skill.SkillStatusRecord;
import com.xg.platform.contracts.skill.SkillStatusResponse;
import com.xg.platform.contracts.skill.UpdateSkillConfigRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills")
public class SkillController {

    private final SkillConfigService skillConfigService;

    public SkillController(SkillConfigService skillConfigService) {
        this.skillConfigService = skillConfigService;
    }

    @GetMapping("/status")
    public SkillStatusResponse listStatus(@CurrentUserId String userId) {
        return skillConfigService.listStatus(userId);
    }

    @PutMapping("/{skillId}/config")
    public ResponseEntity<?> updateSkillConfig(@CurrentUserId String userId,
                                               @PathVariable String skillId,
                                               @RequestBody(required = false) UpdateSkillConfigRequest request) {
        if (!skillConfigService.secretStorageAvailable()) {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Skill secret storage is not enabled"
            );
            detail.setTitle("Service unavailable");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
        }
        SkillStatusRecord record = skillConfigService.updateSkillConfig(userId, skillId, request);
        return ResponseEntity.ok(record);
    }
}
