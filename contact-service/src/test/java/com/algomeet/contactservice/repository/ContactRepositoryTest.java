// src/test/java/com/algomeet/contactservice/repository/ContactRepositoryTest.java
package com.algomeet.contactservice.repository;

import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false"
})
class ContactRepositoryTest {

    @Autowired
    private ContactRepository repo;

    private UUID A; // current user
    private UUID B;
    private UUID C;

    @BeforeEach
    void setup() {
        A = UUID.randomUUID();
        B = UUID.randomUUID();
        C = UUID.randomUUID();

        // A -> B ACCEPTED
        repo.save(Contact.builder()
                .userKey(A).contactUserKey(B)
                .status(ContactStatus.ACCEPTED)
                .createdAt(Instant.now())
                .build());

        // B -> A ACCEPTED (reverse)
        repo.save(Contact.builder()
                .userKey(B).contactUserKey(A)
                .status(ContactStatus.ACCEPTED)
                .createdAt(Instant.now())
                .build());

        // C -> A PENDING (A has incoming pending from C)
        repo.save(Contact.builder()
                .userKey(C).contactUserKey(A)
                .status(ContactStatus.PENDING)
                .createdAt(Instant.now())
                .build());

        // A -> C PENDING (A sent pending to C; should NOT show in A's findPending)
        repo.save(Contact.builder()
                .userKey(A).contactUserKey(C)
                .status(ContactStatus.PENDING)
                .createdAt(Instant.now())
                .build());
    }

    @Test
    void existsUuidPair_true_ifEitherDirectionExists() {
        // direct direction exists
        assertThat(repo.existsUuidPair(A, B)).isTrue();
        // reverse direction exists
        assertThat(repo.existsUuidPair(B, A)).isTrue();

        // pair that doesn't exist at all
        UUID D = UUID.randomUUID();
        assertThat(repo.existsUuidPair(A, D)).isFalse();
    }

    @Test
    void findAccepted_returnsAllContactsAcceptedByUser() {
        List<UUID> acceptedForA = repo.findAccepted(A);
        assertThat(acceptedForA)
                .containsExactlyInAnyOrder(B)  // A -> B accepted
                .doesNotContain(C);            // A -> C is pending, not accepted

        List<UUID> acceptedForB = repo.findAccepted(B);
        assertThat(acceptedForB).containsExactly(A); // B -> A accepted
    }

    @Test
    void findPending_returnsIncomingRequestsOnly() {
        // Pending requests where contact_user_key = A (i.e., others -> A)
        var pendForA = repo.findPending(A);
        assertThat(pendForA).hasSize(1);
        assertThat(pendForA.get(0).getUserKey()).isEqualTo(C); // C -> A PENDING
        assertThat(pendForA.get(0).getContactUserKey()).isEqualTo(A);

        // Pending requests where contact_user_key = B (should be none)
        assertThat(repo.findPending(B)).isEmpty();
    }

    @Test
    void existsByUserKeyAndContactUserKey_and_findByUserKeyAndContactUserKey_workAsExpected() {
        assertThat(repo.existsByUserKeyAndContactUserKey(A, B)).isTrue();
        assertThat(repo.existsByUserKeyAndContactUserKey(B, A)).isTrue();
        assertThat(repo.existsByUserKeyAndContactUserKey(A, UUID.randomUUID())).isFalse();

        var opt = repo.findByUserKeyAndContactUserKey(A, B);
        assertThat(opt).isPresent();
        assertThat(opt.get().getStatus()).isEqualTo(ContactStatus.ACCEPTED);
    }
}
