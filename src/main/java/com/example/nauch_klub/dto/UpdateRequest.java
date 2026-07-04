package com.example.nauch_klub.dto;

import com.example.nauch_klub.enums.Roles;

public record UpdateRequest(AuthRequest authRequest, String username, Roles role) { }
