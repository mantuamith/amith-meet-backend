package com.algomeet.contactservice.controller;

import com.algomeet.contactservice.config.AuthCtx;
import com.algomeet.contactservice.dto.CommonResponse;
import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.enums.ResponseCode;
import com.algomeet.contactservice.i18n.MessageResolver;
import com.algomeet.contactservice.service.ContactService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@Validated
public class ContactController {

    private final ContactService contactService;
    private final MessageResolver i18n;

    // 1) Send a contact request
    @PostMapping("/request")
    public ResponseEntity<CommonResponse<Void>> sendContactRequest(@RequestParam @NotBlank String receiverId,
                                                                   Authentication authentication) {
        contactService.sendContactRequest(authentication, receiverId);
        return okMsg("success.contact.request_sent");
    }

    // 2) Accept a contact request (caller identified by Authentication.getName())
    @PostMapping("/accept")
    public ResponseEntity<CommonResponse<Void>> acceptContactRequest(@RequestParam @NotBlank String senderId,
                                                                     Authentication authentication) {
        String receiverId = requirePrincipalName(authentication);
        contactService.acceptContactRequest(receiverId, senderId);
        return okMsg("success.contact.accepted");
    }

    // 3) Get accepted contacts (authoritative user_key from JWT)
    @GetMapping
    public ResponseEntity<CommonResponse<List<UserDto>>> getAcceptedContacts(Authentication authentication) {
        UUID userKey = AuthCtx.userKeyFrom(authentication);
        if (userKey == null) return unauthorizedBody();
        List<UserDto> data = contactService.getContactList(userKey);
        return okData("success.contact.list", data);
    }

    // 4) Get pending requests (authoritative user_key from JWT)
    @GetMapping("/pending-requests")
    public ResponseEntity<CommonResponse<List<UserDto>>> getPendingRequests(Authentication authentication) {
        UUID userKey = AuthCtx.userKeyFrom(authentication);
        if (userKey == null) return unauthorizedBody();
        List<UserDto> data = contactService.getPendingRequests(userKey);
        return okData("success.contact.pending_list", data);
    }

    // 5) Remove a contact (caller identified by Authentication.getName())
    @DeleteMapping("/remove")
    public ResponseEntity<CommonResponse<Void>> deleteContact(@RequestParam @NotBlank String contactUserId,
                                                              Authentication authentication) {
        String userId = requirePrincipalName(authentication);
        contactService.deleteContact(userId, contactUserId);
        return okMsg("success.contact.removed");
    }

    // 6) Search users (caller context from Authentication)
    @GetMapping("/search")
    public ResponseEntity<CommonResponse<List<UserDto>>> searchUsers(@RequestParam @NotBlank String query,
                                                                     Authentication authentication) {
        requirePrincipalName(authentication); // ensure 401 if missing
        List<UserDto> data = contactService.searchUsers(query, authentication);
        return okData("success.contact.search", data);
    }

    // 7) Reject a contact request (caller identified by Authentication.getName())
    @PostMapping("/reject")
    public ResponseEntity<CommonResponse<Void>> rejectContactRequest(@RequestParam @NotBlank String senderId,
                                                                     Authentication authentication) {
        String receiverId = requirePrincipalName(authentication);
        contactService.rejectContactRequest(receiverId, senderId);
        return okMsg("success.contact.rejected");
    }

    // ---- helpers ----

    private String requirePrincipalName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    private ResponseEntity<CommonResponse<Void>> okMsg(String key) {
        return ResponseEntity.ok(CommonResponse.of(ResponseCode.OK.getCode(), i18n.msg(key), null));
    }

    private <T> ResponseEntity<CommonResponse<T>> okData(String key, T data) {
        return ResponseEntity.ok(CommonResponse.of(ResponseCode.OK.getCode(), i18n.msg(key), data));
    }

    private <T> ResponseEntity<CommonResponse<T>> unauthorizedBody() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.of(ResponseCode.AUTH_SESSION_REVOKED.getCode(),
                        i18n.msg(ResponseCode.AUTH_SESSION_REVOKED), null));
    }
}
