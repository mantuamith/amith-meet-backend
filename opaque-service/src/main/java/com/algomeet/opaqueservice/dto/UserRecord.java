package com.algomeet.opaqueservice.dto;

import lombok.Data;

//---- Simple in-memory store for demo (use a secure DB in production) ----
public record UserRecord(String username, byte[] registrationRecord, byte[] serverPrivateKey){
}