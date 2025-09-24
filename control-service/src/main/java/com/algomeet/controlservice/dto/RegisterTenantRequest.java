package com.algomeet.controlservice.dto;

import jakarta.validation.constraints.NotNull;

public class RegisterTenantRequest extends TenantRequest{	
	@NotNull
	public Integer getId() {
		return super.getId();
	}

}
