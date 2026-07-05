package com.example.nauch_klub.controllers;

import com.example.nauch_klub.dto.KnowledgeGraphResponse;
import com.example.nauch_klub.services.KnowledgeGraphService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class KnowledgeGraphController {
    private final KnowledgeGraphService graphService;

    public KnowledgeGraphController(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/knowledge")
    @ResponseStatus(HttpStatus.OK)
    public KnowledgeGraphResponse knowledgeGraph(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "2") int depth,
            @RequestParam(defaultValue = "80") int limit) {
        return graphService.getKnowledgeGraph(search, type, depth, limit);
    }
}
