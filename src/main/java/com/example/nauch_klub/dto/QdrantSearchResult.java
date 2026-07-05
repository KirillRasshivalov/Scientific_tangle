package com.example.nauch_klub.dto;

public record QdrantSearchResult(
        String id,
        double score,
        String title,
        String text,
        String domain,
        String geography,
        String sourceType,
        String year
) {
}
