package com.algomeet.controlservice.dto;

import jakarta.validation.constraints.NotNull;

public class RegisterTenantRequest extends TenantRequest{	
	@NotNull(message = "{tenant.id.blank}")
	public Integer getId() {
		return super.getId();
	}

}
