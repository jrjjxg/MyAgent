package com.xg.platform.contracts.conversation;

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

    public static PostMessageRequest of(String content,
                                        InteractionMode interactionMode,
                                        String providerId) {
        return new PostMessageRequest(content, interactionMode, providerId, List.of(), List.of());
    }

    public static PostMessageRequest withImages(String content,
                                                InteractionMode interactionMode,
                                                String providerId,
                                                List<String> imageArtifactIds) {
        return new PostMessageRequest(content, interactionMode, providerId, imageArtifactIds, List.of());
    }
}
