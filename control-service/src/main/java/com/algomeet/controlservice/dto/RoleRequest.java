package com.algomeet.controlservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class RoleRequest {
    private String id;
	@NotEmpty
    private String name;
    private String description;
}