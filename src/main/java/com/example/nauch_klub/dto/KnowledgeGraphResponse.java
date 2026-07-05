package com.example.nauch_klub.dto;

import java.util.List;

public record KnowledgeGraphResponse(
        List<KnowledgeGraphNode> nodes,
        List<KnowledgeGraphEdge> edges
) {
}
