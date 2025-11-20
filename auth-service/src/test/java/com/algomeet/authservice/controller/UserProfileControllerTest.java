package com.algomeet.authservice.controller;

import com.algomeet.authservice.dto.UserProfileResponse;
import com.algomeet.authservice.dto.UserProfileUpdateRequest;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.exception.GlobalExceptionHandler;
import com.algomeet.authservice.otp.OtpRepository;
import com.algomeet.authservice.otp.PendingPasswordResetRepository;
import com.algomeet.authservice.otp.PendingRegistrationRepository;
import com.algomeet.authservice.service.UserProfileService;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.support.UserProfileTestData;
import com.algomeet.authservice.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserProfileController.class)
@Import(GlobalExceptionHandler.class)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserProfileService userProfileService;

    // Mock beans often required by your security/config; keep them to avoid context load issues
    @MockBean JwtUtil jwtUtil;
    @MockBean SidCache sidCache;
    @MockBean OtpRepository otpRepository;
    @MockBean PendingPasswordResetRepository pendingPasswordResetRepository;
    @MockBean PendingRegistrationRepository pendingRegistrationRepository;

    @Test
    @WithMockUser(username = "admin")
    void getProfile_returnsWrappedSuccess() throws Exception {
        UUID id = UserProfileTestData.anyProfileId();
        UserProfileResponse resp = UserProfileTestData.profile(id, true);

        when(userProfileService.findById(id)).thenReturn(resp);

        mvc.perform(get("/auth/user-profiles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.securityQuestionsEnabled").value(true));
    }

    @Test
    @WithMockUser(username = "admin", roles  = {"ADMIN"})
    void updateProfile_returnsWrappedUpdateSuccess() throws Exception {
        UUID id = UserProfileTestData.anyProfileId();
        UserProfileUpdateRequest req = UserProfileTestData.updateReq(false, "ADMIN", 42);
        UserProfileResponse updated = UserProfileTestData.profile(id, false);

        when(userProfileService.updateProfile(
                eq(id),
                any(UserProfileUpdateRequest.class)
        )).thenReturn(updated);

        mvc.perform(
                        put("/auth/user-profiles/{id}", id)
                                .with(csrf()) // CSRF is required for state-changing methods
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ResponseCode.UPDATE_USER_PROFILE_SUCCESS.name()))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.securityQuestionsEnabled").value(false));
    }

    @Test
    @WithAnonymousUser
    void getProfile_unauthenticated_returns401() throws Exception {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        mvc.perform(get("/auth/user-profiles/{id}", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateProfile_withoutCsrf_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        UserProfileUpdateRequest req = UserProfileTestData.updateReq(false, "ADMIN", 42);

        mvc.perform(put("/auth/user-profiles/{id}", id)
                        // .with(csrf())  <-- intentionally omitted
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateProfile_malformedJson_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        String badJson = "{ \"securityQuestionsEnabled\": tru"; // broken token

        mvc.perform(put("/auth/user-profiles/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getProfile_invalidUuid_returns400() throws Exception {
        mvc.perform(get("/auth/user-profiles/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateProfile_serviceThrows_returns5xx() throws Exception {
        UUID id = UUID.randomUUID();
        var req = UserProfileTestData.updateReq(false, "ADMIN", 42);
        when(userProfileService.updateProfile(eq(id), any()))
                .thenThrow(new RuntimeException("boom"));

        mvc.perform(put("/auth/user-profiles/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getProfile_nullBody_returns200WithNullData() throws Exception {
        UUID id = UUID.randomUUID();
        when(userProfileService.findById(id)).thenReturn(null);

        mvc.perform(get("/auth/user-profiles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data").doesNotExist()); // or isEmpty/Null depending on CommonResponse
    }





}
