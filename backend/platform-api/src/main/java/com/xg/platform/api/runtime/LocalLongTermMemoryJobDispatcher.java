package com.xg.platform.api.runtime;

import com.xg.platform.runtime.LongTermMemoryExtractionRequest;
import com.xg.platform.runtime.LongTermMemoryJobDispatcher;
import com.xg.platform.runtime.LongTermMemoryJobProcessor;
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
