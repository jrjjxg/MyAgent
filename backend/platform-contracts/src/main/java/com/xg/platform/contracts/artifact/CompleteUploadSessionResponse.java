package com.xg.platform.contracts.artifact;

public record CompleteUploadSessionResponse(
        UploadSessionStatusResponse session,
        UploadResponse upload
) {
}
