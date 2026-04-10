package com.xg.platform.research.port;

import com.xg.platform.contracts.research.ResearchDraftRecord;

import java.util.Optional;

public interface ResearchDraftRepository {

    Optional<ResearchDraftRecord> findActiveDraft(String userId, String threadId);

    ResearchDraftRecord save(String userId, ResearchDraftRecord draftRecord);

    void clear(String userId, String threadId);

    void deleteByThread(String userId, String threadId);
}
