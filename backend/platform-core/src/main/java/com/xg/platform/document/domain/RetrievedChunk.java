package com.xg.platform.document.domain;

import java.io.Serializable;

public record RetrievedChunk(
        DocumentChunk chunk,
        int score
) implements Serializable {
}
