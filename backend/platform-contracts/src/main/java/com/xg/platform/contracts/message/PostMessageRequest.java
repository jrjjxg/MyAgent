package com.xg.platform.contracts.message;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record PostMessageRequest(
        String content,
        InteractionMode interactionMode,
        String providerId,
        List<String> imageArtifactIds,
        List<String> documentIds
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public PostMessageRequest {
        imageArtifactIds = imageArtifactIds == null ? List.of() : List.copyOf(imageArtifactIds);
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
    }

    public PostMessageRequest(String content,
                              InteractionMode interactionMode,
                              String providerId) {
        this(content, interactionMode, providerId, List.of(), List.of());
    }

    public PostMessageRequest(String content,
                              InteractionMode interactionMode,
                              String providerId,
                              List<String> imageArtifactIds) {
        this(content, interactionMode, providerId, imageArtifactIds, List.of());
    }
}
