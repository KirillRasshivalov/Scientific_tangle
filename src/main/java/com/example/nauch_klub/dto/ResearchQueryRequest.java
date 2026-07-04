package com.example.nauch_klub.dto;

import java.util.Map;

public record ResearchQueryRequest(
        String query,
        Map<String, String> filters
) {
}
