package com.algomeet.contactservice.service;


import com.algomeet.contactservice.client.UserClient;
import com.algomeet.contactservice.dto.ContactActionResponse;
import com.algomeet.contactservice.dto.RelationStatus;
import com.algomeet.contactservice.dto.SearchUserResponse;
import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import com.algomeet.contactservice.repository.ContactRepository;
import com.algomeet.multitenancy.context.TenantContext;

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
import static com.algomeet.contactservice.util.MessageUtil.wrapWithBraces;

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

    @Transactional
    public ContactActionResponse sendContactRequest(Authentication auth, String receiverLoginOrId) {
        log.debug("Sending contact request: sender={}, receiver={}", auth.getName(), receiverLoginOrId);

        try {
            if (receiverLoginOrId == null || receiverLoginOrId.isBlank()) {
                return ContactActionResponse.builder()
                        .code("RECEIVER_NOT_FOUND")
                        .message("Enter a valid handle, email, or phone.")
                        .build();
            }

            UUID me = AuthCtx.userKeyFrom(auth);
            String senderLogin = auth.getName();
            if (me == null) {
                log.warn("AuthCtx returned null, resolving sender key from login: {}", senderLogin);
                me = resolveKeyFlexible(senderLogin);
            }

            UUID other = resolveKeyFlexible(receiverLoginOrId);
            if (other == null) {
                return ContactActionResponse.builder()
                        .code("RECEIVER_NOT_FOUND")
                        .message("No matching Algomeet user.")
                        .build();
            }

            if (me.equals(other)) {
                return ContactActionResponse.builder()
                        .code("SELF")
                        .message("You can’t add your own account.")
                        .relation(RelationStatus.SELF)
                        .build();
            }

            // ✅ Already friends? (either direction)
            boolean acceptedAB = contactRepository
                    .existsByUserKeyAndContactUserKeyAndStatus(me, other, ContactStatus.ACCEPTED);
            boolean acceptedBA = contactRepository
                    .existsByUserKeyAndContactUserKeyAndStatus(other, me, ContactStatus.ACCEPTED);
            if (acceptedAB || acceptedBA) {
                return ContactActionResponse.builder()
                        .code("ALREADY_FRIEND")
                        .message("Already in your contacts.")
                        .relation(RelationStatus.ALREADY_FRIEND)
                        .user(userClient.byKey(other))
                        .build();
            }

            // ⏳ Outgoing pending already exists?
            boolean pendingOut = contactRepository
                    .existsByUserKeyAndContactUserKeyAndStatus(me, other, ContactStatus.PENDING);
            if (pendingOut) {
                return ContactActionResponse.builder()
                        .code("PENDING_EXISTS")
                        .message("Request already sent.")
                        .relation(RelationStatus.PENDING)
                        .user(userClient.byKey(other))
                        .build();
            }

        Notification notif = Notification.builder()  
        		.receiverIds(Set.of(other.toString()))
        		.type(NotificationType.FRIEND_REQUEST)
        		.title(wrapWithBraces(user.getUsername()) + " sent you a friend request")
        		.body(wrapWithBraces(user.getUsername()) + " sent you a friend request")
        		.deliveryAckRequired(true)
        		.tenantId(TenantContext.getCurrentTenant())
        		.build();
        // Publish
        notificationService.sendPush(notif); 
      log.info("Friend request notification sent from {} to {}", me, other);

                // Ensure reverse row exists
                boolean reverseExists = contactRepository.existsByUserKeyAndContactUserKey(me, other);
                if (!reverseExists) {
                    Contact rev = Contact.builder()
                            .userKey(me)
                            .contactUserKey(other)
                            .userId(senderLogin == null ? null : senderLogin.trim().toLowerCase())
                            .contactUserId(receiverLoginOrId == null ? null : receiverLoginOrId.trim().toLowerCase())
                            .status(ContactStatus.ACCEPTED)
                            .createdAt(Instant.now())
                            .build();
                    contactRepository.save(rev);
                }

                safeNotifyFriendAccepted(me, other);

                return ContactActionResponse.builder()
                        .code("AUTO_ACCEPTED")
                        .message("You’re now connected.")
                        .relation(RelationStatus.ALREADY_FRIEND)
                        .user(userClient.byKey(other))
                        .build();
            }

            // 🆕 Create new pending request
            Contact c = Contact.builder()
                    .userKey(me)
                    .contactUserKey(other)
                    .userId(senderLogin == null ? null : senderLogin.trim().toLowerCase())
                    .contactUserId(receiverLoginOrId.trim().toLowerCase())
                    .status(ContactStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
            contactRepository.save(c);

            safeNotifyFriendRequest(me, other);

            return ContactActionResponse.builder()
                    .code("OK")
                    .message("Request sent.")
                    .relation(RelationStatus.PENDING)
                    .user(userClient.byKey(other))
                    .build();

        } catch (Exception e) {
            log.warn("sendContactRequest failed: {}", e.toString());
            return ContactActionResponse.builder()
                    .code("ERROR")
                    .message("We couldn’t send your request. Please try again.")
                    .build();
        }

        UserDto user = userClient.byKey(me);

        Notification notif = Notification.builder()       
        		// Set receiver
        		.receiverIds(Set.of(other.toString()))                 
        		.type(NotificationType.FRIEND_REQUEST_ACCEPTED)        
        		.title(wrapWithBraces(user.getUsername()) + " accepted your friend request")
        		.body(wrapWithBraces(user.getUsername()) + " accepted your friend request")
        		.deliveryAckRequired(true)
        		.tenantId(TenantContext.getCurrentTenant())
        		.build();
        // Publish
        notificationService.sendPush(notif); 

        log.info("Friend request acceptance notification sent from {} to {}", me, other);

    }

    @Transactional
    public ContactActionResponse acceptContactRequest(String receiverLogin, String senderLoginOrId) {
        log.debug("Accepting contact request: receiver={}, sender={}", receiverLogin, senderLoginOrId);

        try {
            UUID me = currentUserKey(receiverLogin);
            UUID other = resolveKeyFlexible(senderLoginOrId);

            if (me == null || other == null) {
                return ContactActionResponse.builder()
                        .code("NO_REQUEST_FOUND")
                        .message("No contact request found.")
                        .build();
            }
            if (me.equals(other)) {
                return ContactActionResponse.builder()
                        .code("SELF")
                        .message("Invalid accept operation.")
                        .relation(RelationStatus.SELF)
                        .build();
            }

            Contact req = contactRepository
                    .findByUserKeyAndContactUserKeyAndStatus(other, me, ContactStatus.PENDING)
                    .orElse(null);

            if (req == null) {

                return ContactActionResponse.builder()
                        .code("NO_REQUEST_FOUND")
                        .message("No contact request found.")
                        .build();
            }

            // Accept
            req.setStatus(ContactStatus.ACCEPTED);
            contactRepository.save(req);

            boolean reverseExists = contactRepository.existsByUserKeyAndContactUserKey(me, other);
            if (!reverseExists) {
                contactRepository.save(Contact.builder()
                        .userKey(me)
                        .contactUserKey(other)
                        .userId(receiverLogin == null ? null : receiverLogin.trim().toLowerCase())
                        .contactUserId(senderLoginOrId == null ? null : senderLoginOrId.trim().toLowerCase())
                        .status(ContactStatus.ACCEPTED)
                        .createdAt(Instant.now())
                        .build());
            }

            safeNotifyFriendAccepted(me, other);

            return ContactActionResponse.builder()
                    .code("OK")
                    .message("Contact added.")
                    .relation(RelationStatus.ALREADY_FRIEND)
                    .user(userClient.byKey(other))
                    .build();

        } catch (Exception e) {
            log.warn("acceptContactRequest failed: {}", e.toString());
            return ContactActionResponse.builder()
                    .code("ERROR")
                    .message("We couldn’t accept the request. Please try again.")
                    .build();
        }
    }


    public List<UserDto> getContactList(UUID userKey) {
        log.debug("Fetching accepted contacts for userId={}", userKey);
        List<UUID> accepted = contactRepository.findAccepted(userKey);

        log.info("Accepted contact list size for {}: {}", userKey, accepted.size());
        return userClient.getUsersByKeys(accepted);
    }

    public List<UserDto> getPendingRequests(UUID userKey) {
        log.debug("Fetching pending requests for userId={}", userKey);
        List<Contact> pending = contactRepository.findPending(userKey);
        List<UUID> uuids = pending.stream()
                .map(Contact::getUserKey)
                .collect(Collectors.toList());
        log.info("Pending requests count for {}: {}", userKey, uuids.size());
        return userClient.getUsersByKeys(uuids);

    }

    @Transactional
    public ContactActionResponse rejectContactRequest(String userLogin, String contactLoginOrId) {
        log.debug("Rejecting contact request: user={}, contact={}", userLogin, contactLoginOrId);

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
               
        Notification notif = Notification.builder()       
        		// Set receiver
        		.receiverIds(Set.of(other.toString()))                 
        		.type(NotificationType.FRIEND_REQUEST_REJECTED)        
        		.title(wrapWithBraces(userLogin) + " rejected your friend request")
        		.body(wrapWithBraces(userLogin) + " rejected your friend request")
        		.deliveryAckRequired(true)
        		.tenantId(TenantContext.getCurrentTenant())
        		.build();
        // Publish
        notificationService.sendPush(notif); 
    }

    @Transactional
    public ContactActionResponse deleteContact(String userId, String contactUserId) {
        log.debug("Deleting contact between {} and {}", userId, contactUserId);

        try {
            UUID me = currentUserKey(userId);
            UUID other = resolveKeyFlexible(contactUserId);

            if (me == null || other == null) {
                return ContactActionResponse.builder()
                        .code("NO_CONTACT_FOUND")
                        .message("No contact found.")
                        .build();
            }
            if (me.equals(other)) {
                return ContactActionResponse.builder()
                        .code("SELF")
                        .message("Invalid operation on your own account.")
                        .relation(RelationStatus.SELF)
                        .build();
            }

            // We treat DELETE as removing an accepted contact.
            var a1 = contactRepository.findByUserKeyAndContactUserKeyAndStatus(me, other, ContactStatus.ACCEPTED);
            var a2 = contactRepository.findByUserKeyAndContactUserKeyAndStatus(other, me, ContactStatus.ACCEPTED);

            boolean anyAccepted = false;
            if (a1.isPresent()) {
                contactRepository.delete(a1.get());
                anyAccepted = true;
                log.info("Deleted accepted contact {} -> {}", me, other);
            }
            if (a2.isPresent()) {
                contactRepository.delete(a2.get());
                anyAccepted = true;
                log.info("Deleted accepted contact {} -> {}", other, me);
            }

            if (!anyAccepted) {
                // If only pending exists, don’t silently remove friendship — let FE call cancel/reject endpoints
                boolean pendingAB = contactRepository
                        .existsByUserKeyAndContactUserKeyAndStatus(me, other, ContactStatus.PENDING);
                boolean pendingBA = contactRepository
                        .existsByUserKeyAndContactUserKeyAndStatus(other, me, ContactStatus.PENDING);

                if (pendingAB || pendingBA) {
                    return ContactActionResponse.builder()
                            .code("ONLY_PENDING")
                            .message("There’s a pending request. Use cancel/reject instead.")
                            .relation(RelationStatus.PENDING)
                            .user(userClient.byKey(other))
                            .build();
                }

                return ContactActionResponse.builder()
                        .code("NO_CONTACT_FOUND")
                        .message("No contact found.")
                        .build();
            }



            return ContactActionResponse.builder()
                    .code("OK")
                    .message("Contact removed.")
                    .relation(RelationStatus.NOT_FOUND)
                    .user(userClient.byKey(other))
                    .build();

        } catch (Exception e) {
            log.warn("deleteContact failed: {}", e.toString());
            return ContactActionResponse.builder()
                    .code("ERROR")
                    .message("We couldn’t remove the contact. Please try again.")
                    .build();
        }
    }

    public SearchUserResponse searchUsersWithStatus(String query, Principal auth) {
        log.debug("Searching users with query='{}' by {}", query, auth.getName());

        if (query == null || query.isBlank()) {
            log.info("Empty search query received from {}", auth.getName());
            return SearchUserResponse.builder()
                    .code("EMPTY_QUERY")
                    .message("Please enter something to search.")
                    .relation(RelationStatus.EMPTY_QUERY)
                    .build();
        }

        UUID me = currentUserKey(auth.getName());

        UserDto hit = userClient.exact(query);
        if (hit == null || hit.getUserKey() == null) {
            log.info("No user found for query='{}'", query);
            return SearchUserResponse.builder()
                    .code("NOT_FOUND")
                    .message("No matching Algomeet user.")
                    .relation(RelationStatus.NOT_FOUND)
                    .build();
        }

        UUID cand = UUID.fromString(hit.getUserKey());
        if (cand.equals(me)) {
            log.debug("Skipping self match for {}", me);
            return SearchUserResponse.builder()
                    .code("SELF")
                    .message("You can’t add yourself.")
                    .relation(RelationStatus.SELF)
                    .build();
        }

        // accepted = both directions already handled by your repo helper
        var acceptedKeys = new HashSet<>(contactRepository.findAccepted(me));
        if (acceptedKeys.contains(cand)) {
            log.info("User {} already friends with {}", me, cand);
            return SearchUserResponse.builder()
                    .code("ALREADY_FRIEND")
                    .message("Already in your contacts.")
                    .relation(RelationStatus.ALREADY_FRIEND)
                    .user(hit)
                    .build();
        }

        // ✅ Pending checks: outgoing (me -> cand) and incoming (cand -> me)
        boolean pendingOutgoing = contactRepository
                .existsByUserKeyAndContactUserKeyAndStatus(me, cand, ContactStatus.PENDING);
        if (pendingOutgoing) {
            log.info("User {} has an OUTGOING pending request to {}", me, cand);
            return SearchUserResponse.builder()
                    .code("PENDING")
                    .message("Request already sent.")
                    .relation(RelationStatus.PENDING) // reuse existing enum
                    .user(hit)
                    .build();
        }

        boolean pendingIncoming = contactRepository
                .existsByUserKeyAndContactUserKeyAndStatus(cand, me, ContactStatus.PENDING);
        if (pendingIncoming) {
            log.info("User {} has an INCOMING pending request from {}", me, cand);
            return SearchUserResponse.builder()
                    .code("PENDING_INCOMING")
                    .message("They’ve sent you a request.")
                    .relation(RelationStatus.PENDING) // same relation, different code/message for CTA
                    .user(hit)
                    .build();
        }

        log.info("Search hit found for query='{}': {}", query, hit.getUsername());
        return SearchUserResponse.builder()
                .code("OK")
                .message("User found.")
                .relation(RelationStatus.FOUND)
                .user(hit)
                .build();
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

        return UUID.fromString(String.valueOf(u.getUserKey()));
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

    private void safeNotifyFriendRequest(UUID me, UUID other) {
        try {
            UserDto user = userClient.byKey(me);
            Notification notif = Notification.builder()
                    .receiverIds(Set.of(other.toString()))
                    .type(NotificationType.FRIEND_REQUEST)
                    .title(user.getUsername() + " sent you a friend request")
                    .body(user.getUsername() + " sent you a friend request")
                    .deliveryAckRequired(true)
                    .build();
            notificationService.sendPush(notif);
            log.info("Friend request notification sent from {} to {}", me, other);
        } catch (Exception e) {
            log.warn("notify FRIEND_REQUEST failed ({} -> {}): {}", me, other, e.toString());
        }
    }

    private void safeNotifyFriendAccepted(UUID me, UUID other) {
        try {
            UserDto user = userClient.byKey(me);
            Notification notif = Notification.builder()
                    .receiverIds(Set.of(other.toString()))
                    .type(NotificationType.FRIEND_REQUEST_ACCEPTED)
                    .title(user.getUsername() + " accepted your friend request")
                    .body(user.getUsername() + " accepted your friend request")
                    .deliveryAckRequired(true)
                    .build();
            notificationService.sendPush(notif);
            log.info("Friend request acceptance notification sent from {} to {}", me, other);
        } catch (Exception e) {
            log.warn("notify FRIEND_REQUEST_ACCEPTED failed ({} -> {}): {}", me, other, e.toString());
        }
    }
}
