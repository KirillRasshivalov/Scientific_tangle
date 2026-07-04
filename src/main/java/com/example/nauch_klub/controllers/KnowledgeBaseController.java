package com.example.nauch_klub.controllers;

import com.example.nauch_klub.dto.KnowledgeUploadResponse;
import com.example.nauch_klub.services.KnowledgeBaseUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {
    private final KnowledgeBaseUploadService uploadService;

    public KnowledgeBaseController(KnowledgeBaseUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.OK)
    public KnowledgeUploadResponse upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ANALYST".equals(authority.getAuthority()))) {
            String username = authentication == null ? "anonymous" : authentication.getName();
            uploadService.logDeniedUpload(username, file);
            throw new AccessDeniedException("Only analyst can upload files to knowledge base");
        }

        return uploadService.upload(file, authentication.getName());
    }
}
