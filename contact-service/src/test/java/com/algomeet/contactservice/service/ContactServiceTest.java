package com.algomeet.contactservice.service;

import com.algomeet.contactservice.client.UserClient;
import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.entity.Contact;
import com.algomeet.contactservice.entity.ContactStatus;
import com.algomeet.contactservice.repository.ContactRepository;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.service.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock private ContactRepository contactRepository;
    @Mock private UserClient userClient;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ContactService service;

    private UUID me;
    private UUID other;

    private UsernamePasswordAuthenticationToken authWithUserKey() {
        var a = new UsernamePasswordAuthenticationToken("user@example.com", null, List.of());
        a.setDetails(Map.of("user_key", me.toString()));
        return a;
    }

    private static UserDto mkUser(String username, String key) {
        var u = new UserDto();
        u.setUsername(username);
        u.setUserKey(key);
        return u;
    }

    @BeforeEach
    void setup() {
        me = UUID.randomUUID();
        other = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- sendContactRequest ----------

    @Test
    void sendContactRequest_success_persistsPending_andSendsNotification() {
        var auth = authWithUserKey();

        when(contactRepository.existsUuidPair(me, other)).thenReturn(false);
        when(userClient.byKey(me)).thenReturn(mkUser("Me", me.toString()));

        ArgumentCaptor<Contact> saved = ArgumentCaptor.forClass(Contact.class);
        doAnswer(inv -> inv.getArgument(0)).when(contactRepository).save(any(Contact.class));

        service.sendContactRequest(auth, other.toString()); // receiver id as UUID string

        verify(contactRepository).existsUuidPair(me, other);
        verify(contactRepository).save(saved.capture());
        verify(notificationService).sendPush(any(Notification.class));

        var c = saved.getValue();
        assertThat(c.getUserKey()).isEqualTo(me);
        assertThat(c.getContactUserKey()).isEqualTo(other);
        assertThat(c.getStatus()).isEqualTo(ContactStatus.PENDING);
        assertThat(c.getCreatedAt()).isNotNull();
    }

    @Test
    void sendContactRequest_self_throws() {
        var auth = authWithUserKey();
        assertThatThrownBy(() -> service.sendContactRequest(auth, me.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add yourself");
        verifyNoInteractions(contactRepository);
    }

    // ---------- acceptContactRequest ----------

    @Test
    void acceptContactRequest_success_updatesRequest_createsReverse_andSendsNotification() {
        // SecurityContext used by currentUserKey(...)
        SecurityContextHolder.getContext().setAuthentication(authWithUserKey());

        Contact pending = Contact.builder()
                .id(1L)
                .userKey(other)
                .contactUserKey(me)
                .status(ContactStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(contactRepository.findByUserKeyAndContactUserKey(other, me))
                .thenReturn(Optional.of(pending));
        when(contactRepository.existsByUserKeyAndContactUserKey(me, other))
                .thenReturn(false);
        when(userClient.byKey(me)).thenReturn(mkUser("Me", me.toString()));

        service.acceptContactRequest("user@example.com", other.toString());

        // Request updated to ACCEPTED
        verify(contactRepository, atLeastOnce()).save(argThat(c ->
                c.getUserKey().equals(other) &&
                c.getContactUserKey().equals(me) &&
                c.getStatus() == ContactStatus.ACCEPTED
        ));

        // Reverse contact created
        verify(contactRepository, atLeastOnce()).save(argThat(c ->
                c.getUserKey().equals(me) &&
                c.getContactUserKey().equals(other) &&
                c.getStatus() == ContactStatus.ACCEPTED
        ));

        // Notification sent
        verify(notificationService).sendPush(any(Notification.class));
    }

    // ---------- getContactList / getPendingRequests ----------

    @Test
    void getContactList_returnsUsersFromUserClient() {
        List<UUID> accepted = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(contactRepository.findAccepted(me)).thenReturn(accepted);

        List<UserDto> users = List.of(mkUser("a", accepted.get(0).toString()), mkUser("b", accepted.get(1).toString()));
        when(userClient.getUsersByKeys(accepted)).thenReturn(users);

        var result = service.getContactList(me);
        assertThat(result).hasSize(2);
        verify(contactRepository).findAccepted(me);
        verify(userClient).getUsersByKeys(accepted);
    }

    @Test
    void getPendingRequests_mapsPendingUserKeysAndCallsUserClient() {
        Contact p1 = Contact.builder().userKey(UUID.randomUUID()).contactUserKey(me).status(ContactStatus.PENDING).build();
        Contact p2 = Contact.builder().userKey(UUID.randomUUID()).contactUserKey(me).status(ContactStatus.PENDING).build();
        when(contactRepository.findPending(me)).thenReturn(List.of(p1, p2));
        when(userClient.getUsersByKeys(anyList())).thenReturn(List.of());

        var out = service.getPendingRequests(me);
        assertThat(out).isEmpty();

        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(userClient).getUsersByKeys(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(p1.getUserKey(), p2.getUserKey());
    }

    // ---------- reject / delete ----------

    @Test
    void rejectContactRequest_deletesBothDirections_ifPresent() {
        SecurityContextHolder.getContext().setAuthentication(authWithUserKey());

        Contact a = Contact.builder().userKey(me).contactUserKey(other).status(ContactStatus.PENDING).build();
        Contact b = Contact.builder().userKey(other).contactUserKey(me).status(ContactStatus.PENDING).build();

        when(contactRepository.findByUserKeyAndContactUserKey(me, other)).thenReturn(Optional.of(a));
        when(contactRepository.findByUserKeyAndContactUserKey(other, me)).thenReturn(Optional.of(b));

        service.rejectContactRequest("user@example.com", other.toString());

        verify(contactRepository).delete(a);
        verify(contactRepository).delete(b);
    }

    @Test
    void deleteContact_deletesBothDirections_ifPresent() {
        SecurityContextHolder.getContext().setAuthentication(authWithUserKey());

        Contact a = Contact.builder().userKey(me).contactUserKey(other).status(ContactStatus.ACCEPTED).build();
        Contact b = Contact.builder().userKey(other).contactUserKey(me).status(ContactStatus.ACCEPTED).build();

        when(contactRepository.findByUserKeyAndContactUserKey(me, other)).thenReturn(Optional.of(a));
        when(contactRepository.findByUserKeyAndContactUserKey(other, me)).thenReturn(Optional.of(b));

        service.deleteContact("user@example.com", other.toString());

        verify(contactRepository).delete(a);
        verify(contactRepository).delete(b);
    }

    // ---------- searchUsers ----------

    @Test
    void searchUsers_blankQuery_returnsEmptyList() {
        Principal principal = () -> "user@example.com";
        var out = service.searchUsers("  ", principal);
        assertThat(out).isEmpty();
        verifyNoInteractions(userClient, contactRepository);
    }

    @Test
    void searchUsers_selfHit_returnsEmpty() {
        Principal principal = () -> "user@example.com";
        // currentUserKey will resolve from SecurityContext -> set it
        SecurityContextHolder.getContext().setAuthentication(authWithUserKey());

        UserDto meUser = mkUser("me", me.toString());
        when(userClient.exact("whatever")).thenReturn(meUser);      // search query returns self

        var out = service.searchUsers("whatever", principal);
        assertThat(out).isEmpty();
    }

    @Test
    void searchUsers_existingRelation_returnsEmpty() {
        Principal principal = () -> "user@example.com";
        SecurityContextHolder.getContext().setAuthentication(authWithUserKey());

        UserDto hit = mkUser("alice", other.toString());
        when(userClient.exact("alice")).thenReturn(hit);

        // simulate that 'other' is already accepted or pending
        when(contactRepository.findAccepted(me)).thenReturn(List.of(other));
        when(contactRepository.findPending(me)).thenReturn(List.of());

        var out = service.searchUsers("alice", principal);
        assertThat(out).isEmpty();
    }

    @Test
    void searchUsers_newCandidate_returnsListWithHit() {
        Principal principal = () -> "user@example.com";
        SecurityContextHolder.getContext().setAuthentication(authWithUserKey());

        UserDto hit = mkUser("alice", other.toString());
        when(userClient.exact("alice")).thenReturn(hit);
        when(contactRepository.findAccepted(me)).thenReturn(List.of());
        when(contactRepository.findPending(me)).thenReturn(List.of());

        var out = service.searchUsers("alice", principal);
        assertThat(out).hasSize(1)
                .first()
                .extracting(UserDto::getUsername)
                .isEqualTo("alice");
    }
}
