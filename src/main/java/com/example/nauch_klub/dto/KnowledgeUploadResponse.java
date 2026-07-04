package com.example.nauch_klub.dto;

import java.time.Instant;

public record KnowledgeUploadResponse(
        String requestId,
        String status,
        String bucket,
        String objectName,
        String originalFilename,
        long size,
        String contentType,
        Instant uploadedAt
) {
}
