package com.algomeet.meetservice.client;

import com.algomeet.meetservice.model.Room;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Optional;
import java.util.UUID;

@FeignClient(name = "user-directory", url = "${feign.user-service.url:http://127.0.0.1:65535}")
public interface UserDirectoryClient {

    @GetMapping("/internal/users/lookup/exact")
    User exact(@RequestParam("q") String q);

    default Optional<User> find(String q) {
        try {
            return Optional.ofNullable(exact(q));
        } catch (feign.FeignException.NotFound e) {
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record User(
            UUID userKey,
            String id,
            String email,
            String username,
            String displayName,
            String tenantId,
            Room personalRoom
    ) {}
}
