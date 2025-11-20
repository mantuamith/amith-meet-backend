package com.algomeet.controlservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;


public class CreateRoleRequest extends RoleRequest{
	@NotEmpty(message="{role.add.id-blank}")
    @Pattern(
        regexp = "^(?i)ROLE_[A-Za-z0-9_-]+$",
        message = "{role.add.invalid-id}"
    )
	public String getId() {
		return super.getId();
	}
}