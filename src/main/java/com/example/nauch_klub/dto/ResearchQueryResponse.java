package com.example.nauch_klub.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ResearchQueryResponse(
        String requestId,
        String status,
        String answer,
        double confidence,
        Instant updatedAt,
        List<String> graphPath,
        List<Map<String, String>> sources
) {
}
