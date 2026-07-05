package com.example.nauch_klub.dto;

public record KnowledgeGraphEdge(
        String id,
        String source,
        String target,
        String type,
        String label
) {
}
