package com.algomeet.contactservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.contactservice.service.ContactService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/contacts")
@RequiredArgsConstructor
@Validated
public class InternalContactController {
    private final ContactService contactService;

    @GetMapping("/{userKey}")
    public List<UUID> getAcceptedContacts(@PathVariable UUID userKey) {
        return contactService.getContactListUserKeys(userKey);
    }
}
