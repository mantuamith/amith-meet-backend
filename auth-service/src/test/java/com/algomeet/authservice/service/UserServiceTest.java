// src/test/java/com/algomeet/authservice/service/UserServiceTest.java
package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.config.LocalizationConfig;
import com.algomeet.authservice.dto.PageResponse;
import com.algomeet.authservice.dto.SearchUsersFilter;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.support.PageResponses;
import com.algomeet.authservice.support.TestFilters;
import com.algomeet.authservice.support.TestUsers;
import com.algomeet.authservice.util.MessageUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Import(LocalizationConfig.class) // include your config
class UserServiceTest {

    @Mock
    private UserClient userClient;

    private UserService userService;
    
    @Mock MessageSource messageSource;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(userClient);
        
        // Initialize messageSource into the MessageUtil constructor
     	new MessageUtil(messageSource);
    }

    @Test
    void searchUsers_delegatesToFeignAndReturnsPage() {
        var filter = TestFilters.filter("al", "a@b.com", "9999", 1, 5, "id", "desc", 7);
        var items = List.of(TestUsers.user(1L, "alice", "a@b.com"));
        PageResponse<UserResponse> page = PageResponses.of(items, 1, 5, 12);

        when(userClient.findAll(
                eq(filter.getUsername()),
                eq(filter.getEmail()),
                eq(filter.getPhoneNumber()),
                eq(filter.getPage()),
                eq(filter.getSize()),
                eq(filter.getSortBy()),
                eq(filter.getDirection()),
                eq(filter.getTenantId())
        )).thenReturn(ResponseEntity.ok(page));

        PageResponse<UserResponse> result = userService.searchUsers(filter);

        assertNotNull(result);
        assertEquals(1, result.getPage());
        assertEquals(5, result.getSize());
        assertEquals(12, result.getTotalElements());
        assertEquals("alice", result.getContent().get(0).getUsername());

        verify(userClient, times(1)).findAll(
                eq("al"), eq("a@b.com"), eq("9999"),
                eq(1), eq(5), eq("id"), eq("desc"), eq(7));
    }

    @Test
    void findUserById_returnsUser() {
        UserResponse u = TestUsers.user(42L, "neo", "neo@matrix.io");
        when(userClient.findUserById(42L)).thenReturn(u);

        UserResponse result = userService.findUserById(42L);

        assertNotNull(result);
        assertEquals(42L, result.getId());
        assertEquals("neo", result.getUsername());
        verify(userClient).findUserById(42L);
    }

    @Test
    void findUserByUserKey_returnsUser() {
        UUID key = UUID.randomUUID();
        UserResponse u = TestUsers.user(100L, "trinity", "tri@matrix.io");
        u.setUserKey(key);
        when(userClient.findUserByUserKey(key)).thenReturn(u);

        UserResponse result = userService.findUserByUserKey(key);

        assertNotNull(result);
        assertEquals(key, result.getUserKey());
        assertEquals("trinity", result.getUsername());
        verify(userClient).findUserByUserKey(key);
    }

    @Test
    void searchUsers_handlesFeignException_returnsNullPage() {
        var filter = new SearchUsersFilter(); // minimal
        when(userClient.findAll(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("boom"));

        PageResponse<UserResponse> result = userService.searchUsers(filter);

        // current implementation returns null on exception
        assertNull(result);
    }
}
