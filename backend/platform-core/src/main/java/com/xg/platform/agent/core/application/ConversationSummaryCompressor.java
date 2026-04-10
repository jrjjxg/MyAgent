package com.xg.platform.agent.core.application;

import com.xg.platform.contracts.conversation.MessageRecord;

import java.util.List;

public interface ConversationSummaryCompressor {

    String summarizeHistory(String userId, List<MessageRecord> historicalMessages);

    String extendSummary(String userId, String existingSummary, List<MessageRecord> newlyHistoricalMessages);
}
