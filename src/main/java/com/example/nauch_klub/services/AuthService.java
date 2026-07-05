package com.example.nauch_klub.services;

import com.example.nauch_klub.config.JwtService;
import com.example.nauch_klub.dto.AuthRequest;
import com.example.nauch_klub.dto.AuthResponse;
import com.example.nauch_klub.dto.UpdateRequest;
import com.example.nauch_klub.entities.User;
import com.example.nauch_klub.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("ANALYST");

        user = userRepository.save(user);
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }

    @Transactional
    public AuthResponse updateRole(UpdateRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.authRequest().username(), request.authRequest().password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(request.role().toString());
        user = userRepository.save(user);

        String newToken = jwtService.generateToken(user);
        return new AuthResponse(newToken, request.username(), request.role().toString());
    }
}
