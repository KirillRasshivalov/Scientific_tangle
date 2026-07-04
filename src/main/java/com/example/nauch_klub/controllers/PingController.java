package com.example.nauch_klub.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ping")
public class PingController {
    private static final Logger log = LoggerFactory.getLogger(PingController.class);

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public String ping() {
        log.info("Проверка сервера на доступность.");
        return "pong";
    }
}
