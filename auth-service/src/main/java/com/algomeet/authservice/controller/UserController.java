package com.algomeet.authservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.SearchUsersFilter;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.service.UserService;

import lombok.AllArgsConstructor;

@RequestMapping("/auth/users")
@RestController
@AllArgsConstructor
public class UserController {
	private UserService userService;
	
	@GetMapping
	@PreAuthorize("hasAnyRole('SA','ADMIN')")
	public ResponseEntity<? extends CommonResponse<?>> getUsers(SearchUsersFilter filter){
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, userService.searchUsers(filter)));
	}	
	// GET user 
	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('SA','ADMIN')")
	public ResponseEntity<CommonResponse<UserResponse>> findById(@PathVariable Long id) {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, userService.findUserById(id)));
	}

	// GET user 
	@GetMapping("/by-user-key/{userKey}")
	@PreAuthorize("hasAnyRole('SA','ADMIN')")
	public ResponseEntity<CommonResponse<UserResponse>> getUserByUserKey(@PathVariable UUID userKey) {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, userService.findUserByUserKey(userKey)));
	}	
}
