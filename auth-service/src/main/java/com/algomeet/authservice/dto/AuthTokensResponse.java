package com.algomeet.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// dto/AuthTokensResponse.java  (reuse your login shape)
@AllArgsConstructor
@Data
public class AuthTokensResponse {
  private String type;          // "EMAIL" or "PHONE"
  private String message;       // e.g., "Registration successful."

}
