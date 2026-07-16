package com.algomeet.chatservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "user-directory-chat", url = "${user.service.url:http://localhost:8082}")
public interface UserDirectoryClient {

    @GetMapping("/internal/users/lookup/exact")
    UserLookup exact(@RequestParam("q") String q);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserLookup(UUID userKey, String username, String email) {}
}
