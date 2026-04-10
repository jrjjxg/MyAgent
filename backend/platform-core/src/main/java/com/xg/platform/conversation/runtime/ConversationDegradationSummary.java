package com.xg.platform.conversation.runtime;

import java.util.List;

record ConversationDegradationSummary(boolean degraded, List<String> reasons) {
}
