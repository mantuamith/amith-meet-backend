package com.algomeet.xmpp.chatservice.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "contact-service", url = "${feign.client.contact-service.url}")
public interface ContactClient {

	@GetMapping("/internal/contacts/{userKey}")
    public List<UUID> getAcceptedContacts(@PathVariable UUID userKey);
}

