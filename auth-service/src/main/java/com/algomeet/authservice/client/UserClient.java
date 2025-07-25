package com.algomeet.authservice.client;

import com.algomeet.authservice.dto.UserRequest;
import com.algomeet.authservice.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", url = "http://localhost:8082") // You can externalize this later
public interface UserClient {

    @PostMapping("/internal/users")
    UserResponse createUser(@RequestBody UserRequest request);

    @GetMapping("/internal/users/username/{username}")
    UserResponse getUserByUsername(@PathVariable String username);

    @GetMapping("/internal/users/email/{email}")
    UserResponse getUserByEmail(@PathVariable("email") String email);
}