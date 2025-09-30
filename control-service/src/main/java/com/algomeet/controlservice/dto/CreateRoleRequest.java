package com.algomeet.controlservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;


public class CreateRoleRequest extends RoleRequest{
	@NotEmpty
	@NotBlank
    @Pattern(
        regexp = "^(?i)ROLE_[A-Za-z0-9_-]+$",
        message = "Role ID must start with 'ROLE_' (case-insensitive)"
    )
	public String getId() {
		return super.getId();
	}
}