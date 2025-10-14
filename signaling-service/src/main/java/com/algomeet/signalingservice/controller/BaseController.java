package com.algomeet.signalingservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BaseController {

    @GetMapping("/health")
    public String healthCheck() {
        return "signaling-service is running.";
    }
}
