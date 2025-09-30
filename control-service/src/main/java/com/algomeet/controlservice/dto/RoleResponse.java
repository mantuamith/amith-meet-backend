package com.algomeet.controlservice.dto;

import java.time.Instant;

import lombok.Data;

@Data
public class RoleResponse {
    private String id;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant modifiedAt;
}