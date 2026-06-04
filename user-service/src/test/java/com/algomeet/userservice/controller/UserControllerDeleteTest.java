package com.algomeet.userservice.controller;

import com.algomeet.userservice.client.MediaServiceClient;
import com.algomeet.userservice.model.User;
import com.algomeet.userservice.repository.UserProfileRepository;
import com.algomeet.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for DELETE /internal/users/email/{email}.
 *
 * Uses standalone MockMvc (no Spring context) to avoid JDK-23 byte-buddy
 * inline-mock limitations with @WebMvcTest / @MockBean.
 *
 * Verifies:
 *  1. Successful deletion calls mediaServiceClient with the correct userKey.
 *  2. Deleting a non-existent user returns 404 and never touches the media client.
 *  3. A media-service failure (client throws) does NOT abort account deletion.
 *  4. The correct userKey (from the resolved User entity) is always passed.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerDeleteTest {

    @Mock private UserRepository      userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PasswordEncoder     passwordEncoder;
    @Mock private MediaServiceClient  mediaServiceClient;

    @InjectMocks
    private UserController controller;

    private MockMvc mockMvc;

    private static final UUID USER_KEY = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        // Standalone setup — no Spring context, no auto-config, no DB, no byte-buddy issues
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private User stubUser(String email, UUID key) {
        User user = new User();
        user.setId(1L);
        user.setUserKey(key);
        user.setEmail(email);
        user.setUsername("testuser");
        user.setPassword("hashed");
        user.setTenantId(0);
        return user;
    }

    // ── 1. Happy path ─────────────────────────────────────────────────────────

    @Test
    void delete_existingUser_returns200_andCallsMediaCleanup() throws Exception {
        when(userRepository.findByEmailIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(stubUser("alice@example.com", USER_KEY)));

        mockMvc.perform(delete("/internal/users/email/alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deleted successfully"));

        // Media storage-usage cleanup must be called with the correct UUID
        verify(mediaServiceClient).deleteStorageUsage(USER_KEY);
        // User row must actually be removed from DB
        verify(userRepository).deleteByEmail("alice@example.com");
    }

    // ── 2. User not found ─────────────────────────────────────────────────────

    @Test
    void delete_nonExistentUser_returns404_andNeverCallsMediaClient() throws Exception {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/internal/users/email/ghost@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));

        // Media client must not be touched when user doesn't exist
        verifyNoInteractions(mediaServiceClient);
        verify(userRepository, never()).deleteByEmail(any());
    }

    // ── 3. Media-service failure does NOT block deletion ──────────────────────

    @Test
    void delete_whenMediaClientThrows_userIsStillDeleted() throws Exception {
        when(userRepository.findByEmailIgnoreCase("bob@example.com"))
                .thenReturn(Optional.of(stubUser("bob@example.com", USER_KEY)));

        // Simulate an unexpected exception leaking from the media client
        doThrow(new RuntimeException("media-service unexpected failure"))
                .when(mediaServiceClient).deleteStorageUsage(any());

        // Controller catches this internally — must still return 200
        mockMvc.perform(delete("/internal/users/email/bob@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deleted successfully"));

        verify(mediaServiceClient).deleteStorageUsage(USER_KEY);
        // Deletion must proceed despite the media client failure
        verify(userRepository).deleteByEmail("bob@example.com");
    }

    // ── 4. Correct userKey is resolved from the User entity ───────────────────

    @Test
    void delete_passesExactUserKeyFromEntityToMediaClient() throws Exception {
        UUID specificKey = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(userRepository.findByEmailIgnoreCase("carol@example.com"))
                .thenReturn(Optional.of(stubUser("carol@example.com", specificKey)));

        mockMvc.perform(delete("/internal/users/email/carol@example.com"))
                .andExpect(status().isOk());

        // Must use the key from the looked-up entity, not any hardcoded or default value
        verify(mediaServiceClient).deleteStorageUsage(specificKey);
    }
}
