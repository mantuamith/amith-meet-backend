package com.algomeet.contactservice.service;


import com.algomeet.contactservice.client.UserClient;
import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import com.algomeet.contactservice.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.algomeet.contactservice.config.AuthCtx;


import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserClient userClient;
    private final NotificationService notificationService;

    public void addContact(String userId, String contactUserId) {
        log.debug("Attempting to add contact: userId={}, contactUserId={}", userId, contactUserId);

        if (userId.equals(contactUserId)) {
            log.error("User {} tried to add themselves as a contact.", userId);
            throw new IllegalArgumentException("Cannot add yourself as a contact.");
        }

        if (contactRepository.existsByUserIdAndContactUserId(userId, contactUserId)) {
            log.info("Contact already exists between {} and {}", userId, contactUserId);
            return; // Already added
        }

        log.debug("Validating existence of contactUserId={}", contactUserId);
        userClient.getUserById(contactUserId); // validate existence

        Contact contact = Contact.builder()
                .userId(userId)
                .contactUserId(contactUserId)
                .build();
        contactRepository.save(contact);
        log.info("Contact successfully added: {} -> {}", userId, contactUserId);
    }

    public List<UserDto> getContactsForUser(String userId) {
        log.debug("Fetching contacts for userId={}", userId);
        List<String> contactUserIds = contactRepository.findContactUserIdsByUserId(userId);
        log.info("Found {} contacts for userId={}", contactUserIds.size(), userId);
        return userClient.getUsersByIds(contactUserIds);
    }

    public void sendContactRequest(Authentication auth, String receiverLoginOrId) {
        log.debug("Sending contact request: sender={}, receiver={}", auth.getName(), receiverLoginOrId);

        UUID me = AuthCtx.userKeyFrom(auth);
        String senderLogin = auth.getName();
        if (me == null) {
            log.warn("AuthCtx returned null, resolving sender key from login: {}", senderLogin);
            me = resolveKeyFlexible(senderLogin);
        }

        UUID other = resolveKeyFlexible(receiverLoginOrId);
        if (me.equals(other)) {
            log.error("User {} attempted to send a contact request to themselves.", senderLogin);
            throw new IllegalArgumentException("Cannot add yourself.");
        }

        if (contactRepository.existsUuidPair(me, other)) {
            log.error("Duplicate contact request: {} -> {}", me, other);
            throw new RuntimeException("Contact or request already exists.");
        }

        Contact c = Contact.builder()
                .userKey(me)
                .contactUserKey(other)
                .userId(senderLogin == null ? null : senderLogin.trim().toLowerCase())
                .contactUserId(receiverLoginOrId.trim().toLowerCase())
                .status(ContactStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        contactRepository.save(c);
        log.info("Contact request saved: {} -> {}", me, other);

        UserDto user = userClient.byKey(me);

        Notification notif = Notification.builder()  
        		.receiverIds(Set.of(other.toString()))
        		.type(NotificationType.FRIEND_REQUEST)
        		.title(user.getUsername() + " sent you a friend request")
        		.body(user.getUsername() + " sent you a friend request")
        		.deliveryAckRequired(true)
        		.build();
        // Publish
        notificationService.sendPush(notif); 
      log.info("Friend request notification sent from {} to {}", me, other);

    }

    public void acceptContactRequest(String receiverLogin, String senderLoginOrId) {
        log.debug("Accepting contact request: receiver={}, sender={}", receiverLogin, senderLoginOrId);

        UUID me = currentUserKey(receiverLogin);
        UUID other = resolveKeyFlexible(senderLoginOrId);

        Contact req = contactRepository.findByUserKeyAndContactUserKey(other, me)
                .orElseThrow(() -> {
                    log.error("No contact request found from {} to {}", other, me);
                    return new RuntimeException("No contact request found");
                });

        req.setStatus(ContactStatus.ACCEPTED);
        contactRepository.save(req);
        log.info("Contact request accepted: {} <-> {}", me, other);

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
            log.debug("Reverse contact entry created: {} <-> {}", me, other);
        }

        UserDto user = userClient.byKey(me);

        Notification notif = Notification.builder()       
        		// Set receiver
        		.receiverIds(Set.of(other.toString()))                 
        		.type(NotificationType.FRIEND_REQUEST_ACCEPTED)        
        		.title(user.getUsername() + " accepted your friend request")
        		.body(user.getUsername() + " accepted your friend request")
        		.deliveryAckRequired(true)
        		.build();
        // Publish
        notificationService.sendPush(notif); 

        log.info("Friend request acceptance notification sent from {} to {}", me, other);

    }

    public List<UserDto> getContactList(UUID userKey) {
        log.debug("Fetching accepted contacts for userId={}", userKey);
        List<UUID> accepted = contactRepository.findAccepted(userKey);

        log.info("Accepted contact list size for {}: {}", userKey, accepted.size());
        return userClient.getUsersByKeys(accepted);
    }

    public List<UserDto> getPendingRequests(UUID userKey) {
        log.debug("Fetching pending requests for userId={}", userKey);
        List<UUID> pending = contactRepository.findPending(userKey);
        log.info("Pending requests count for {}: {}", userKey, pending.size());
        return userClient.getUsersByKeys(pending);
    }

    public void rejectContactRequest(String userLogin, String contactLoginOrId) {
        log.debug("Rejecting contact request: user={}, contact={}", userLogin, contactLoginOrId);
        UUID me = currentUserKey(userLogin);
        UUID other = resolveKeyFlexible(contactLoginOrId);

        contactRepository.findByUserKeyAndContactUserKey(me, other)
                .ifPresent(contact -> {
                    contactRepository.delete(contact);
                    log.info("Deleted contact request {} -> {}", me, other);
                });
        contactRepository.findByUserKeyAndContactUserKey(other, me)
                .ifPresent(contact -> {
                    contactRepository.delete(contact);
                    log.info("Deleted contact request {} -> {}", other, me);
                });
    }

    public void deleteContact(String userId, String contactUserId) {
        log.debug("Deleting contact between {} and {}", userId, contactUserId);
        contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .ifPresent(contact -> {
                    contactRepository.delete(contact);
                    log.info("Deleted contact {} -> {}", userId, contactUserId);
                });

        contactRepository.findByUserIdAndContactUserId(contactUserId, userId)
                .ifPresent(contact -> {
                    contactRepository.delete(contact);
                    log.info("Deleted contact {} -> {}", contactUserId, userId);
                });
    }

    public List<UserDto> searchUsers(String query, Principal auth) {
        log.debug("Searching users with query='{}' by {}", query, auth.getName());

        if (query == null || query.isBlank()) {
            log.info("Empty search query received from {}", auth.getName());
            return List.of();
        }

        UUID me = currentUserKey(auth.getName());
        UserDto hit = userClient.exact(query);
        if (hit == null || hit.getUserKey() == null) {
            log.info("No user found for query='{}'", query);
            return List.of();
        }

        UUID cand = UUID.fromString(hit.getUserKey());
        if (cand.equals(me)) {
            log.debug("Skipping self match for {}", me);
            return List.of();
        }

        var accepted = new HashSet<>(contactRepository.findAccepted(me));
        var pending  = new HashSet<>(contactRepository.findPending(me));
        if (accepted.contains(cand) || pending.contains(cand)) {
            log.info("User {} already has accepted/pending relation with {}", me, cand);
            return List.of();
        }

        log.info("Search hit found for query='{}': {}", query, hit.getUsername());
        return List.of(hit);
    }

    private UUID resolveKeyFromLogin(String login) {
        log.debug("Resolving user key from login={}", login);
        UserDto u = userClient.exact(login);
        if (u == null || u.getUserKey() == null) {
            log.error("Unknown user: {}", login);
            throw new IllegalArgumentException("Unknown user: " + login);
        }
        return UUID.fromString(u.getUserKey());
    }

    private UUID resolveKeyFlexible(String q) {
        log.debug("Resolving key flexibly for identifier='{}'", q);
        if (q == null || q.isBlank()) {
            log.error("Empty identifier provided for key resolution.");
            throw new IllegalArgumentException("Empty identifier");
        }
        try {
            return UUID.fromString(q);
        } catch (IllegalArgumentException ignore) {
            log.debug("Identifier is not a UUID, trying userClient.exact lookup.");
        }

        UserDto u = userClient.exact(q);
        if (u == null || u.getId() == null) {
            log.error("User not found for identifier='{}'", q);
            throw new IllegalArgumentException("User not found: " + q);
        }

        return java.util.UUID.fromString(String.valueOf(u.getUserKey()));
    }

    private UUID currentUserKey(String currentLogin) {
        log.debug("Resolving current user key for login={}", currentLogin);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map<?,?> m) {
            Object uk = m.get("user_key");
            if (uk instanceof String s && !s.isBlank()) {
                try {
                    UUID parsed = UUID.fromString(s);
                    log.debug("Found user_key in auth details: {}", parsed);
                    return parsed;
                } catch (IllegalArgumentException ignored) {
                    log.warn("Invalid UUID format for user_key: {}", s);
                }
            }
        }
        log.debug("Falling back to resolveKeyFromLogin for login={}", currentLogin);
        return resolveKeyFromLogin(currentLogin);
    }
}
