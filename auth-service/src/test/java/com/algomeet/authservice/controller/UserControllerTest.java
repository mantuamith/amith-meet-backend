// src/test/java/com/algomeet/authservice/controller/UserControllerTest.java
package com.algomeet.authservice.controller;

import com.algomeet.authservice.config.LocalizationConfig;
import com.algomeet.authservice.dto.PageResponse;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.otp.OtpRepository;
import com.algomeet.authservice.otp.PendingPasswordResetRepository;
import com.algomeet.authservice.otp.PendingRegistrationRepository;
import com.algomeet.authservice.service.UserService;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.support.PageResponses;
import com.algomeet.authservice.support.TestUsers;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.authservice.util.MessageUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LocalizationConfig.class) // include your config
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    @MockBean
    JwtUtil jwtUtil;
    @MockBean
    SidCache sidCache;

    @MockBean
    OtpRepository otpRepository;
    @MockBean
    PendingPasswordResetRepository pendingPasswordResetRepository;
    @MockBean
    PendingRegistrationRepository pendingRegistrationRepository;
    
    @Autowired MessageSource messageSource;
    
	@BeforeEach
	void init() {
		// Initialize messageSource into the MessageUtil constructor
		new MessageUtil(messageSource);
	}

    @Test
    void getUsers_returnsPagedUsers() throws Exception {
        List<UserResponse> content = List.of(
                TestUsers.user(1L, "alice", "alice@example.com"),
                TestUsers.user(2L, "bob", "bob@example.com")
        );
        PageResponse<UserResponse> page = PageResponses.of(content, 0, 10, 25);

        when(userService.searchUsers(any())).thenReturn(page);

        mvc.perform(get("/auth/users")
                        .param("username", "a")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "id")
                        .param("direction", "desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // CommonResponse wrapper → expect "code" and "data"
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(25))
                .andExpect(jsonPath("$.data.content[0].username").value("alice"))
                .andExpect(jsonPath("$.data.content[1].username").value("bob"));
    }

    @Test
    void findById_returnsUser() throws Exception {
        UserResponse u = TestUsers.user(10L, "carol", "carol@example.com");
        when(userService.findUserById(10L)).thenReturn(u);

        mvc.perform(get("/auth/users/10").accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value("SUCCESS"))
           .andExpect(jsonPath("$.data.id").value(10))
           .andExpect(jsonPath("$.data.username").value("carol"));
    }

    @Test
    void getUserByUserKey_returnsUser() throws Exception {
        UUID userKey = UUID.randomUUID();
        UserResponse u = TestUsers.user(20L, "dave", "dave@example.com");
        u.setUserKey(userKey);

        when(userService.findUserByUserKey(userKey)).thenReturn(u);

        mvc.perform(get("/auth/users/by-user-key/{userKey}", userKey)
                        .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value("SUCCESS"))
           .andExpect(jsonPath("$.data.userKey").value(userKey.toString()))
           .andExpect(jsonPath("$.data.username").value("dave"));
    }
}
