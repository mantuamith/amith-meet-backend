package com.algomeet.contactservice.repository;

import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    @Query("SELECT c.contactUserId FROM Contact c WHERE c.userId = :userId")
    List<String> findContactUserIdsByUserId(String userId);

    boolean existsByUserIdAndContactUserId(String userId, String contactUserId);

    List<Contact> findByUserIdAndStatus(String userId, ContactStatus status);

    List<Contact> findByContactUserIdAndStatus(String contactUserId, ContactStatus status);

    Optional<Contact> findByUserIdAndContactUserId(String userId, String contactUserId);

}