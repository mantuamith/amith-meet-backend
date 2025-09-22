package com.algomeet.authservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/auth/users")
@RestController
@AllArgsConstructor
public class UserController {
	private UserService userService;
	
	@GetMapping()
	public ResponseEntity<? extends CommonResponse<?>> getUsers(SearchUsersFilter filter){
		log.info("filter {}", filter);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, userService.findAll(filter)));
	}		

	// GET user 
	@GetMapping("/{id}")
	public ResponseEntity<CommonResponse<UserResponse>> getUserById(@PathVariable Long id) {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, userService.findUserById(id)));
	}

	// GET user 
	@GetMapping("/user-key/{userKey}")
	public ResponseEntity<CommonResponse<UserResponse>> getUserByUserKey(@PathVariable UUID userKey) {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, userService.findUserByUserKey(userKey)));
	}
	
	
}
