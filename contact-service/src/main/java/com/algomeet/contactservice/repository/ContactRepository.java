package com.algomeet.contactservice.repository;

import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    @Query("SELECT c.contactUserId FROM Contact c WHERE c.userId = :userId")
    List<String> findContactUserIdsByUserId(String userId);

    boolean existsByUserIdAndContactUserId(String userId, String contactUserId);

    List<Contact> findByUserIdAndStatus(String userId, ContactStatus status);

    List<Contact> findByContactUserIdAndStatus(String contactUserId, ContactStatus status);

    Optional<Contact> findByUserIdAndContactUserId(String userId, String contactUserId);

    @Query("""
SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END
FROM Contact c
WHERE (c.userKey = :a AND c.contactUserKey = :b)
   OR (c.userKey = :b AND c.contactUserKey = :a)
""")
    boolean existsUuidPair(@Param("a") UUID a, @Param("b") UUID b);

    boolean existsByUserKeyAndContactUserKey(UUID userKey, UUID contactUserKey);

    Optional<Contact> findByUserKeyAndContactUserKey(UUID userKey, UUID contactUserKey);

    @Query("""
      select c from Contact c
      where c.contactUserKey = :me and c.status = :status
    """)
    List<Contact> findIncomingByStatus(@Param("me") UUID me, @Param("status") ContactStatus status);

    @Query("""
      select c from Contact c
      where c.userKey = :me and c.status = :status
    """)
    List<Contact> findOutgoingByStatus(@Param("me") UUID me, @Param("status") ContactStatus status);

    @Query("""
      SELECT DISTINCT CASE
        WHEN c.userKey = :userKey THEN c.contactUserKey
        ELSE c.userKey
      END
      FROM Contact c
      WHERE (c.userKey = :userKey OR c.contactUserKey = :userKey)
        AND c.status = :status
    """)
    List<java.util.UUID> findCounterpartyKeysByStatus(
            @Param("userKey") java.util.UUID userKey,
            @Param("status") ContactStatus status
    );

    default List<UUID> findAccepted(UUID key) {
        return findCounterpartyKeysByStatus(key, ContactStatus.ACCEPTED);
    }

    default List<UUID> findPending(UUID key)  {
        return findCounterpartyKeysByStatus(key, ContactStatus.PENDING);
    }


    List<Contact> findByContactUserKeyAndStatus(UUID userId, ContactStatus contactStatus);


}