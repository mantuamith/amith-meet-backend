package com.algomeet.mediaservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BaseController {

    @GetMapping("/health")
    public String healthCheck() {
        return "media-service is running.";
    }
}
