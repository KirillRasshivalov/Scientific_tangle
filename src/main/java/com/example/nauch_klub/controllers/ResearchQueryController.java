package com.example.nauch_klub.controllers;

import com.example.nauch_klub.dto.ResearchQueryRequest;
import com.example.nauch_klub.dto.ResearchQueryResponse;
import com.example.nauch_klub.services.ResearchQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research")
public class ResearchQueryController {
    private final ResearchQueryService researchQueryService;

    public ResearchQueryController(ResearchQueryService researchQueryService) {
        this.researchQueryService = researchQueryService;
    }

    @PostMapping("/query")
    @ResponseStatus(HttpStatus.OK)
    public ResearchQueryResponse query(
            @RequestBody ResearchQueryRequest request,
            Authentication authentication) {
        String username = authentication == null ? "anonymous" : authentication.getName();
        return researchQueryService.handleQuery(request, username);
    }
}
