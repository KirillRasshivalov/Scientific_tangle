package com.example.nauch_klub.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ping")
public class PingController {

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public String ping() {
        return "pong";
    }
}
