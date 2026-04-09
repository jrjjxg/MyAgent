package com.xg.platform.agent.core.chat;

import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.contracts.document.DocumentRecord;

import java.util.List;
import java.util.Locale;

public class ChatRouterService {

    private static final List<String> DOCUMENT_QA_TERMS = List.of(
            "paper",
            "pdf",
            "document",
            "file",
            "appendix",
            "section",
            "summarize this",
            "summarise this",
            "read this",
            "review this",
            "compare these papers",
            "\u8bba\u6587",
            "\u6587\u6863",
            "\u6587\u4ef6",
            "\u603b\u7ed3",
            "\u9605\u8bfb",
            "\u89e3\u91ca",
            "\u5bf9\u6bd4",
            "pdf"
    );

    public ChatRouteDecision route(AgentExecutionRequest request,
                                   List<DocumentRecord> documents) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return chatDecision();
        }
        if (request.selectedDocumentIds() != null && !request.selectedDocumentIds().isEmpty()) {
            return documentDecision();
        }
        return fallbackDecision(request.message(), documents, request.uploadedFiles().size());
    }

    public static boolean hasExplicitDocumentQaIntent(String message,
                                                      List<DocumentRecord> documents,
                                                      int uploadedFileCount) {
        boolean hasDocs = (documents != null && !documents.isEmpty()) || uploadedFileCount > 0;
        if (!hasDocs || message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return DOCUMENT_QA_TERMS.stream().anyMatch(normalized::contains);
    }

    private ChatRouteDecision fallbackDecision(String message,
                                               List<DocumentRecord> documents,
                                               int uploadedFileCount) {
        if (hasExplicitDocumentQaIntent(message, documents, uploadedFileCount)) {
            return documentDecision();
        }
        return chatDecision();
    }

    private static ChatRouteDecision chatDecision() {
        return new ChatRouteDecision(ChatRouteKind.CHAT, "chat", true);
    }

    private static ChatRouteDecision documentDecision() {
        return new ChatRouteDecision(ChatRouteKind.DOCUMENT_QA, "document-qa", true);
    }
}
