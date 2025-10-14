package com.algomeet.contactservice.controller;

import com.algomeet.contactservice.dto.SearchUserResponse;
import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    //1. Send a contact request
    @PostMapping("/request")
    public ResponseEntity<String> sendContactRequest(@RequestParam String receiverId, Authentication auth) {

        contactService.sendContactRequest(auth, receiverId);
        return ResponseEntity.ok("Contact request sent.");
    }

    // 2. Accept a contact request
    @PostMapping("/accept")
    public ResponseEntity<String> acceptContactRequest(@RequestParam String senderId, Principal principal) {
        String receiverId = principal.getName();
        contactService.acceptContactRequest(receiverId, senderId);
        return ResponseEntity.ok("Contact request accepted.");
    }

    // 3. Get accepted contacts
    @GetMapping
    public ResponseEntity<List<UserDto>> getAcceptedContacts(Principal principal) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Map details = (Map) auth.getDetails();
        return ResponseEntity.ok(contactService.getContactList(UUID.fromString((String) details.get("user_key"))));
    }

    //  4. Get pending requests
    @GetMapping("/pending-requests")
    public ResponseEntity<List<UserDto>> getPendingRequests(Principal principal) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Map details = (Map) auth.getDetails();
        return ResponseEntity.ok(contactService.getPendingRequests(UUID.fromString((String) details.get("user_key")) ));
    }

    @DeleteMapping("/remove")
    public ResponseEntity<String> deleteContact(@RequestParam String contactUserId, Principal principal) {
        String userId = principal.getName();
        contactService.deleteContact(userId, contactUserId);
        return ResponseEntity.ok("Contact removed.");
    }

    @GetMapping("/search")
    public ResponseEntity<SearchUserResponse> searchUsers(@RequestParam String query, Principal principal) {
        return ResponseEntity.ok(contactService.searchUsersWithStatus(query, principal));
    }

    @PostMapping("/reject")
    public ResponseEntity<String> rejectContactRequest(@RequestParam String senderId, Principal principal) {
        String receiverId = principal.getName();

        contactService.rejectContactRequest(receiverId, senderId);
        return ResponseEntity.ok("Contact request rejected.");
    }

}
