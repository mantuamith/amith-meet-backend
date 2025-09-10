package com.algomeet.contactservice.client;

import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "user-service", url = "${feign.client.user-service.url}")
public interface UserClient {
    @GetMapping("/internal/users/{id}")
    UserDto getUserById(@PathVariable("id") String userId);

    @PostMapping("/internal/users/batch")
    List<UserDto> getUsersByIds(@RequestBody List<String> userIds);

    @GetMapping("/internal/users/search")
    List<UserDto> searchUsers(@RequestParam("query") String query);

    @GetMapping("/internal/users/lookup/exact")
    UserDto exact(@RequestParam("q") String q);

    @PostMapping("/internal/users/batch/keys")
    List<UserDto> getUsersByKeys(@RequestBody List<String> userKeys);

    // Convenience: resolve by UUID too (user-service exact accepts UUID)
    default UserDto byKey(java.util.UUID key) {
        return exact(key.toString());
    }
}

