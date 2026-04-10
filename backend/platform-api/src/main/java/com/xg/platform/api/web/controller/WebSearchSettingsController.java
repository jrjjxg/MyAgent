package com.xg.platform.api.web.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.api.search.WebSearchConfigService;
import com.xg.platform.contracts.tooling.UpdateWebSearchSettingsRequest;
import com.xg.platform.contracts.tooling.WebSearchSettingsRecord;
import com.xg.platform.contracts.tooling.WebSearchSettingsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web-settings")
public class WebSearchSettingsController {

    private final WebSearchConfigService webSearchConfigService;

    public WebSearchSettingsController(WebSearchConfigService webSearchConfigService) {
        this.webSearchConfigService = webSearchConfigService;
    }

    @GetMapping("/search")
    public WebSearchSettingsResponse getSettings(@CurrentUserId String userId) {
        return webSearchConfigService.getSettings(userId);
    }

    @PutMapping("/search")
    public ResponseEntity<?> updateSettings(@CurrentUserId String userId,
                                            @RequestBody(required = false) UpdateWebSearchSettingsRequest request) {
        if (request != null
                && request.tavilyApiKey() != null
                && !request.tavilyApiKey().isBlank()
                && !webSearchConfigService.secretStorageAvailable()) {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Web search API key storage is not enabled"
            );
            detail.setTitle("Service unavailable");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
        }
        WebSearchSettingsRecord record = webSearchConfigService.updateSettings(userId, request);
        return ResponseEntity.ok(record);
    }
}
