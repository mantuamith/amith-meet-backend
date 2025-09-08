package com.algomeet.contactservice.controller;

import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    //1. Send a contact request
    @PostMapping("/request")
    public ResponseEntity<String> sendContactRequest(@RequestParam String receiverId, Principal principal) {
        String senderId = principal.getName(); // authenticated user
        contactService.sendContactRequest(senderId, receiverId);
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
        return ResponseEntity.ok(contactService.getContactList(principal.getName()));
    }

    //  4. Get pending requests
    @GetMapping("/pending-requests")
    public ResponseEntity<List<UserDto>> getPendingRequests(Principal principal) {
        return ResponseEntity.ok(contactService.getPendingRequests(principal.getName()));
    }

    @DeleteMapping("/remove")
    public ResponseEntity<String> deleteContact(@RequestParam String contactUserId, Principal principal) {
        String userId = principal.getName();
        contactService.deleteContact(userId, contactUserId);
        return ResponseEntity.ok("Contact removed.");
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam String query,  Principal principal) {
        return ResponseEntity.ok(contactService.searchUsers(query, principal));
    }

    @PostMapping("/reject")
    public ResponseEntity<String> rejectContactRequest(@RequestParam String senderId, Principal principal) {
        String receiverId = principal.getName();
        contactService.rejectContactRequest(receiverId, senderId);
        return ResponseEntity.ok("Contact request rejected.");
    }

}
