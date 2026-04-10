package com.xg.platform.api.web.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.api.model.ModelProviderConfigService;
import com.xg.platform.contracts.shared.model.ModelProviderStatusRecord;
import com.xg.platform.contracts.shared.model.ModelProviderStatusResponse;
import com.xg.platform.contracts.shared.model.UpdateModelProviderConfigRequest;
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
@RequestMapping("/model-settings")
public class ModelSettingsController {

    private final ModelProviderConfigService modelProviderConfigService;

    public ModelSettingsController(ModelProviderConfigService modelProviderConfigService) {
        this.modelProviderConfigService = modelProviderConfigService;
    }

    @GetMapping("/providers")
    public ModelProviderStatusResponse listProviders(@CurrentUserId String userId) {
        return modelProviderConfigService.listStatus(userId);
    }

    @PutMapping("/providers/{providerId}")
    public ResponseEntity<?> updateProvider(@CurrentUserId String userId,
                                            @PathVariable String providerId,
                                            @RequestBody(required = false) UpdateModelProviderConfigRequest request) {
        if (request != null
                && request.apiKey() != null
                && !request.apiKey().isBlank()
                && !modelProviderConfigService.secretStorageAvailable()) {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Model API key storage is not enabled"
            );
            detail.setTitle("Service unavailable");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
        }
        ModelProviderStatusRecord record = modelProviderConfigService.updateProviderConfig(userId, providerId, request);
        return ResponseEntity.ok(record);
    }
}
