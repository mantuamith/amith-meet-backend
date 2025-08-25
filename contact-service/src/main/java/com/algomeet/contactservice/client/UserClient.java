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
}
