package com.example.nauch_klub.controllers;

import com.example.nauch_klub.dto.AuthRequest;
import com.example.nauch_klub.dto.AuthResponse;
import com.example.nauch_klub.dto.UpdateRequest;
import com.example.nauch_klub.services.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse register(@RequestBody AuthRequest request) {
        log.info("Пришел запрос на регистрацию пользователя: " + request.username());
        return authService.register(request);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@RequestBody AuthRequest request) {
        log.info("Пришел запрос на авторизацию пользователя: " + request.username());
        return authService.login(request);
    }

    @PostMapping("/role")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse updateRole(@RequestBody UpdateRequest request) {
        log.info("Пришел запрос на смену роли от: " + request.authRequest().username());
        return authService.updateRole(request);
    }
}
