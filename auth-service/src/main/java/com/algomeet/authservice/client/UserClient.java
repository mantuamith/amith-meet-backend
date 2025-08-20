package com.algomeet.authservice.client;

import com.algomeet.authservice.dto.UserRequest;
import com.algomeet.authservice.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "user-service", url = "${feign.client.user-service.url}") // You can externalize this later
public interface UserClient {

    @PostMapping("/internal/users")
    Map<String, Object> createUser(@RequestBody UserRequest request);

    @GetMapping("/internal/users/username/{username}")
    UserResponse getUserByUsername(@PathVariable String username);

    @GetMapping("/internal/users/email/{email}")
    UserResponse getUserByEmail(@PathVariable("email") String email);

    @DeleteMapping("/internal/users/email/{email}")
    void deleteUserByEmail(@PathVariable("email") String email);


}