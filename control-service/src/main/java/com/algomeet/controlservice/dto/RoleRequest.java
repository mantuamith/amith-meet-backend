package com.algomeet.controlservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class RoleRequest {
    private String id;
	@NotEmpty(message = "{role.add.empty-name}")
    private String name;
    private String description;
}