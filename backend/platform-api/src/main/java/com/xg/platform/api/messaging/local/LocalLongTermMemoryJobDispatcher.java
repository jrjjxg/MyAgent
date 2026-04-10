package com.xg.platform.api.messaging.local;

import com.xg.platform.memory.port.LongTermMemoryExtractionRequest;
import com.xg.platform.memory.port.LongTermMemoryJobDispatcher;
import com.xg.platform.memory.port.LongTermMemoryJobProcessor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.ExecutorService;

public class LocalLongTermMemoryJobDispatcher implements LongTermMemoryJobDispatcher {

    private final ExecutorService executorService;
    private final ObjectProvider<LongTermMemoryJobProcessor> longTermMemoryJobProcessorProvider;

    public LocalLongTermMemoryJobDispatcher(ExecutorService executorService,
                                            ObjectProvider<LongTermMemoryJobProcessor> longTermMemoryJobProcessorProvider) {
        this.executorService = executorService;
        this.longTermMemoryJobProcessorProvider = longTermMemoryJobProcessorProvider;
    }

    @Override
    public void dispatch(LongTermMemoryExtractionRequest request) {
        executorService.execute(() -> longTermMemoryJobProcessorProvider.getObject().process(request));
    }
}
