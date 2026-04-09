package com.xg.platform.api.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/threads/{threadId}/events")
public class RunEventController {

    private final ThreadRuntimeService threadRuntimeService;
    private final RunEventRepository runEventRepository;

    public RunEventController(ThreadRuntimeService threadRuntimeService,
                              RunEventRepository runEventRepository) {
        this.threadRuntimeService = threadRuntimeService;
        this.runEventRepository = runEventRepository;
    }

    @GetMapping
    public List<RunEvent> listEvents(@CurrentUserId String userId,
                                     @PathVariable String threadId,
                                     @RequestParam(name = "taskId", required = false) String taskId,
                                     @RequestParam(name = "limit", defaultValue = "50") int limit) {
        threadRuntimeService.getThread(userId, threadId);
        if (taskId != null && !taskId.isBlank()) {
            return runEventRepository.listEvents(userId, threadId, taskId);
        }
        return runEventRepository.listEvents(userId, threadId, limit);
    }
}
