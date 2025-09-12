package com.algomeet.contactservice.service;

import com.algomeet.contactservice.client.UserClient;
import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import com.algomeet.contactservice.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.algomeet.contactservice.config.AuthCtx;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import com.algomeet.contactservice.client.UserClient;
import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import com.algomeet.contactservice.repository.ContactRepository;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserClient userClient;
    private final NotificationService notificationService;

    public void addContact(String userId, String contactUserId) {
        if (userId.equals(contactUserId)) {
            throw new IllegalArgumentException("Cannot add yourself as a contact.");
        }

        if (contactRepository.existsByUserIdAndContactUserId(userId, contactUserId)) {
            return; // Already added
        }

        userClient.getUserById(contactUserId); // validate existence
        Contact contact = Contact.builder()
                .userId(userId)
                .contactUserId(contactUserId)
                .build();
        contactRepository.save(contact);
    }

    public List<UserDto> getContactsForUser(String userId) {
        List<String> contactUserIds = contactRepository.findContactUserIdsByUserId(userId);
        return userClient.getUsersByIds(contactUserIds);
    }

    // 1. Send a contact request
    public void sendContactRequest(Authentication auth, String receiverLoginOrId) {
        java.util.UUID me = AuthCtx.userKeyFrom(auth);
        String senderLogin = auth.getName();
        if (me == null) {
            me = resolveKeyFlexible(senderLogin); // fallback for old tokens
        }

        java.util.UUID other = resolveKeyFlexible(receiverLoginOrId);
        if (me.equals(other))
            throw new IllegalArgumentException("Cannot add yourself.");

        if (contactRepository.existsUuidPair(me, other)) {
            throw new RuntimeException("Contact or request already exists.");
        }

        Contact c = Contact.builder()
                .userKey(me)
                .contactUserKey(other)
                // keep legacy ids to avoid FE changes
                .userId(senderLogin == null ? null : senderLogin.trim().toLowerCase())
                .contactUserId(receiverLoginOrId.trim().toLowerCase())
                .status(ContactStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        contactRepository.save(c);

        // Send friend reuquest notification
        UserDto user = userClient.byKey(me);
        Notification notif = new Notification();
        // Set receiver
        notif.setReceiverIds(Set.of(other.toString()));

        notif.setType(NotificationType.FRIEND_REQUEST_RECEIVED);

        notif.setTitle(user.getUsername() + " sent you a friend request");
        notif.setBody(user.getUsername() + " sent you a friend request");
    	notif.setDeliveryAckRequired(true);
        // Publish
        notificationService.sendPush(notif);
    }

    // 2. Accept a contact request
    public void acceptContactRequest(String receiverLogin, String senderLoginOrId) {
        UUID me = currentUserKey(receiverLogin);
        UUID other = resolveKeyFlexible(senderLoginOrId);

        Contact req = contactRepository.findByUserKeyAndContactUserKey(other, me)
                .orElseThrow(() -> new RuntimeException("No contact request found"));
        req.setStatus(ContactStatus.ACCEPTED);
        contactRepository.save(req);

        // Ensure reverse row exists as ACCEPTED
        boolean reverseExists = contactRepository.existsByUserKeyAndContactUserKey(me, other);
        if (!reverseExists) {
            Contact rev = Contact.builder()
                    .userKey(me)
                    .contactUserKey(other)
                    .userId(receiverLogin == null ? null : receiverLogin.trim().toLowerCase())
                    .contactUserId(senderLoginOrId == null ? null : senderLoginOrId.trim().toLowerCase())
                    .status(ContactStatus.ACCEPTED)
                    .createdAt(Instant.now())
                    .build();
            contactRepository.save(rev);
        }

        // Send friend request accepted notification
        UserDto user = userClient.byKey(me);
        Notification notif = new Notification();
        // Set receiver
        notif.setReceiverIds(Set.of(other.toString()));

        notif.setType(NotificationType.FRIEND_REQUEST_ACCEPTED);

        notif.setTitle(user.getUsername() + " accepted your friend request");
        notif.setBody(user.getUsername() + " accepted your friend request");
    	notif.setDeliveryAckRequired(true);
        // Publish
        notificationService.sendPush(notif);
    }

    // 3. Get accepted contacts
    public List<UserDto> getContactList(String userId) {
        List<Contact> accepted = contactRepository.findByUserIdAndStatus(userId, ContactStatus.ACCEPTED);
        List<String> contactUserIds = accepted.stream()
                .map(Contact::getContactUserId)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
        return userClient.getUsersByIds(contactUserIds);
    }

    // 4. Get pending requests sent to this user
    public List<UserDto> getPendingRequests(String userId) {
        List<Contact> pending = contactRepository.findByContactUserIdAndStatus(userId, ContactStatus.PENDING);
        List<String> senderIds = pending.stream()
                .map(Contact::getUserId)
                .collect(Collectors.toList());
        return userClient.getUsersByIds(senderIds);
    }

    public void rejectContactRequest(String userLogin, String contactLoginOrId) {
        UUID me = currentUserKey(userLogin);
        UUID other = resolveKeyFlexible(contactLoginOrId);

        contactRepository.findByUserKeyAndContactUserKey(me, other)
                .ifPresent(contactRepository::delete);
        contactRepository.findByUserKeyAndContactUserKey(other, me)
                .ifPresent(contactRepository::delete);
    }

    public void deleteContact(String userId, String contactUserId) {
        contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .ifPresent(contactRepository::delete);

        contactRepository.findByUserIdAndContactUserId(contactUserId, userId)
                .ifPresent(contactRepository::delete);
    }

    public List<UserDto> searchUsers(String query, Principal auth) {
        if (query == null || query.isBlank())
            return List.of();

        var token = (JwtAuthenticationToken) auth;
        var meKey = UUID.fromString(token.getToken().getClaimAsString("user_key"));

        // Exact candidate by username/email/UUID
        UserDto hit = userClient.exact(query);
        if (hit == null || hit.getId() == null) return List.of();

        var candidateKey = UUID.fromString(hit.getId().toString());
        if (candidateKey.equals(meKey)) return List.of();

        var accepted = new java.util.HashSet<>(contactRepository.findAccepted(meKey));
        var pending  = new java.util.HashSet<>(contactRepository.findPending(meKey));
        if (accepted.contains(candidateKey) || pending.contains(candidateKey))
            return List.of();

        return List.of(hit); // exact only, after exclusions
    }

    private UUID resolveKeyFromLogin(String login) {
        UserDto u = userClient.exact(login);
        if (u == null || u.getUserKey() == null) {
            throw new IllegalArgumentException("Unknown user: " + login);
        }
        return UUID.fromString(u.getUserKey());
    }

    private UUID resolveKeyFlexible(String q) {
        if (q == null || q.isBlank()) throw new IllegalArgumentException("Empty identifier");
        // allow UUID directly
        try { return java.util.UUID.fromString(q); } catch (IllegalArgumentException ignore) {}
        // else via user-service exact (username/email)
        UserDto u = userClient.exact(q);
        if (u == null || u.getId() == null)
            throw new IllegalArgumentException("User not found: " + q);
        return java.util.UUID.fromString(String.valueOf(u.getId()));
    }

    private UUID currentUserKey(String currentLogin) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map<?,?> m) {
            Object uk = m.get("user_key");
            if (uk instanceof String s && !s.isBlank()) {
                try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
            }
        }
        // fallback: resolve from current principal login (username/email)
        return resolveKeyFromLogin(currentLogin);
    }

}