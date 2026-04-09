package com.xg.platform.memory;

import java.io.Serializable;

public record RetrievedChunk(
        DocumentChunk chunk,
        int score
) implements Serializable {
}
