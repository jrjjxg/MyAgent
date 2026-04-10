package com.xg.platform.shared.runtime.async;

import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.research.application.ResearchTaskExecutionService;

public class PlatformTaskProcessor implements TaskProcessor {

    private final DocumentIngestService documentIngestService;
    private final ResearchTaskExecutionService researchTaskExecutionService;

    public PlatformTaskProcessor(DocumentIngestService documentIngestService,
                                 ResearchTaskExecutionService researchTaskExecutionService) {
        this.documentIngestService = documentIngestService;
        this.researchTaskExecutionService = researchTaskExecutionService;
    }

    @Override
    public void process(TaskDispatchRequest request) {
        if (request.taskKind() == TaskKind.INGEST) {
            documentIngestService.process(request);
            return;
        }
        if (request.taskKind() == TaskKind.RESEARCH) {
            researchTaskExecutionService.execute(request);
        }
    }
}
