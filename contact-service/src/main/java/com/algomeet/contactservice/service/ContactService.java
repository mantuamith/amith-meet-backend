package com.algomeet.contactservice.service;

import com.algomeet.contactservice.client.UserClient;
import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import com.algomeet.contactservice.repository.ContactRepository;
import com.algomeet.contactservice.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserClient userClient;

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
    public void sendContactRequest(String senderId, String receiverId) {
        if (contactRepository.existsByUserIdAndContactUserId(senderId, receiverId)) {
            throw new RuntimeException("Contact request already exists.");
        }

        Contact contact = new Contact();
        contact.setUserId(senderId);
        contact.setContactUserId(receiverId);
        contact.setStatus(ContactStatus.PENDING);
        contact.setCreatedAt(Instant.now());

        contactRepository.save(contact);
    }

    // 2. Accept a contact request
    public void acceptContactRequest(String receiverId, String senderId) {
        Contact request = contactRepository.findByUserIdAndContactUserId(senderId, receiverId)
                .orElseThrow(() -> new RuntimeException("No contact request found"));

        request.setStatus(ContactStatus.ACCEPTED);
        contactRepository.save(request);

        // Also add reverse contact if not exists (for bidirectional contact)
        if (!contactRepository.existsByUserIdAndContactUserId(receiverId, senderId)) {
            Contact reverse = new Contact();
            reverse.setUserId(receiverId);
            reverse.setContactUserId(senderId);
            reverse.setStatus(ContactStatus.ACCEPTED);
            reverse.setCreatedAt(Instant.now());
            contactRepository.save(reverse);
        }
    }

    // 3. Get accepted contacts
    public List<UserDto> getContactList(String userId) {
        List<Contact> accepted = contactRepository.findByUserIdAndStatus(userId, ContactStatus.ACCEPTED);
        List<String> contactUserIds = accepted.stream()
                .map(Contact::getContactUserId)
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

    public void rejectContactRequest(String receiverId, String senderId) {
        Contact request = contactRepository.findByUserIdAndContactUserId(senderId, receiverId)
                .orElseThrow(() -> new RuntimeException("No contact request found"));
        contactRepository.delete(request);
    }

    public void deleteContact(String userId, String contactUserId) {
        contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .ifPresent(contactRepository::delete);

        contactRepository.findByUserIdAndContactUserId(contactUserId, userId)
                .ifPresent(contactRepository::delete);
    }

    public List<UserDto> searchUsers(String query, String currentUserId) {
        // 1. Get all matching users
        List<UserDto> allMatches = userClient.searchUsers(query);

        // 2. Get user's existing contact user IDs
        List<String> existingContactIds = contactRepository.findContactUserIdsByUserId(currentUserId);

        // 3. Filter out the current user and existing contacts
        return allMatches.stream()
                .filter(user -> !user.getId().toString().equals(currentUserId)) // Exclude self
                .filter(user -> !existingContactIds.contains(user.getId().toString())) // Exclude already-added
                .toList();
    }
}