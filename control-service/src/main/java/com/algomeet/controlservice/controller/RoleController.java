package com.algomeet.controlservice.controller;

import java.util.List;

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

import com.algomeet.controlservice.dto.CommonResponse;
import com.algomeet.controlservice.dto.CreateRoleRequest;
import com.algomeet.controlservice.dto.RoleRequest;
import com.algomeet.controlservice.dto.RoleResponse;
import com.algomeet.controlservice.enums.ResponseCode;
import com.algomeet.controlservice.exception.RecordNotFoundException;
import com.algomeet.controlservice.exception.RoleIdAlreadyExistsException;
import com.algomeet.controlservice.exception.RoleNameAlreadyExistsException;
import com.algomeet.controlservice.service.RoleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/control/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SA')")
    public ResponseEntity<CommonResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
    	try {
    		RoleResponse savedRole = roleService.createRole(request);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.ADD_ROLE_SUCCESS, savedRole));
    	} catch(RoleIdAlreadyExistsException ex) {
    		return ResponseEntity.badRequest().body(CommonResponse.from(ResponseCode.ROLE_ID_ALREADY_EXISTS, null));
    	} catch(RoleNameAlreadyExistsException ex) {
    		return ResponseEntity.badRequest().body(CommonResponse.from(ResponseCode.ROLE_NAME_ALREADY_EXISTS, null));
    	}       
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SA', 'ADMIN')")
    public ResponseEntity<CommonResponse<RoleResponse>> getRole(@PathVariable String id) {
    	try {
    		RoleResponse savedRole = roleService.getRole(id);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, savedRole));
    	} catch(RoleIdAlreadyExistsException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ROLE_ID_NOT_FOUND, null));
    	}
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SA', 'ADMIN')")
    public ResponseEntity<CommonResponse<List<RoleResponse>>> getAllRoles() {
    	return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, roleService.getAllRoles()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SA')")
    public ResponseEntity<CommonResponse<RoleResponse>> updateRole(@PathVariable String id, @RequestBody RoleRequest request) {       
        try {
    		RoleResponse savedRole = roleService.updateRole(id, request);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.UPDATE_ROLE_SUCCESS, savedRole));
    	} catch(RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ROLE_ID_NOT_FOUND, null));
    	}
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SA')")
    public ResponseEntity<CommonResponse<?>> deleteRole(@PathVariable String id) {       
        try {
    		roleService.deleteRole(id);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.DELETE_ROLE_SUCCESS, null));
    	} catch(RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ROLE_ID_NOT_FOUND, null));
    	}
    }
}


