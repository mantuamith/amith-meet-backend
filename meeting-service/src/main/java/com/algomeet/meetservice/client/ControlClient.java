package com.algomeet.meetservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "control-service", url = "${feign.client.control-service.url}")
public interface ControlClient {
	@GetMapping("/internal/control/tenants/active/ids")	
	ResponseEntity<List<Integer>> getActiveTenantIds();
}
