package com.algomeet.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// RegisterInitResponse.java
@Data @AllArgsConstructor
public class RegisterInitResponse {
    private String transactionId;                 // client uses this in /verify
    private String type;                          // echoes EMAIL/PHONE
    private String message;
}
