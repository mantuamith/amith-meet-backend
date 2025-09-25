package com.algomeet.controlservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.controlservice.service.TenantService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/control/tenants")
@RequiredArgsConstructor
public class TenantInternalController {
	private final TenantService tenantService;

	@GetMapping("/active/ids")	
	public ResponseEntity<List<Integer>> getActiveTenantIds() {
		return ResponseEntity.ok(tenantService.getActiveTenantIds());
	}
}
