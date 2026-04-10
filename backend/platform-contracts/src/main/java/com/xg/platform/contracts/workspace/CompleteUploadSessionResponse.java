package com.xg.platform.contracts.workspace;

public record CompleteUploadSessionResponse(
        UploadSessionStatusResponse session,
        UploadResponse upload
) {
}
