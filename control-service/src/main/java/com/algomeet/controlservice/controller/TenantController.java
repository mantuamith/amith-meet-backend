package com.algomeet.controlservice.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import com.algomeet.controlservice.dto.CommonResponse;
import com.algomeet.controlservice.dto.TenantRequest;
import com.algomeet.controlservice.dto.TenantResponse;
import com.algomeet.controlservice.enums.ResponseCode;
import com.algomeet.controlservice.exception.TenantIdAlreadyExists;
import com.algomeet.controlservice.service.TenantService;
import com.algomeet.controlservice.util.SecurityUtil;

@RestController
@RequestMapping("/control/tenants")
@RequiredArgsConstructor
public class TenantController {
	private final TenantService tenantService;

	@GetMapping
	@PreAuthorize("hasAnyRole('SA','ADMIN')")
	public ResponseEntity<CommonResponse<List<TenantResponse>>> getAllTenants() {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, tenantService.getAllTenants()));
	}

	@GetMapping("/{id}")
	@PreAuthorize("@securityService.isTenantOwner(#id)")
	public ResponseEntity<CommonResponse<TenantResponse>> getTenantById(@PathVariable Integer id) {
		return ResponseEntity.ok(
				CommonResponse.from(ResponseCode.SUCCESS,tenantService.getTenantById(
						// If tenant Id is zero get from user auth session
						id == 0 ? SecurityUtil.getTenantId() : id)
						.orElseGet(null))
				);
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('SA','ADMIN')")
	public ResponseEntity<CommonResponse<TenantResponse>> createTenant(@RequestBody TenantRequest request) {
		TenantResponse saved = null;
		try {
			saved = tenantService.createTenant(request);
		} catch (TenantIdAlreadyExists ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.from(ResponseCode.TENANT_ID_ALREADY_EXISTS,
				saved));
		}
		
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.ADD_TENANT_SUCCESS,
				saved));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('SA','ADMIN') and @securityService.isTenantOwner(#id)")
	public ResponseEntity<CommonResponse<TenantResponse>> updateTenant(@PathVariable Integer id, @RequestBody TenantRequest request) {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.UPDATE_TENANT_SUCCESS,
				tenantService.updateTenant(id, request)));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyRole('SA','ADMIN') and @securityService.isTenantOwner(#id)")
	public ResponseEntity<CommonResponse<TenantResponse>> deleteTenant(@PathVariable Integer id) {
		tenantService.deleteTenant(id);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.DELETE_TENANT_SUCCESS,
				null));
	}
}
