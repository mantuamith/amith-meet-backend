package com.algomeet.opaqueservice.dto;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Controller;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;

// ---- Models ----
public record RegistrationRequestDto(String username, String clientRegistrationMessageBase64) {}
