package com.algomeet.authservice.controller;

import com.algomeet.authservice.dto.SecurityQuestionRequest;
import com.algomeet.authservice.dto.SecurityQuestionResponse;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.otp.OtpRepository;
import com.algomeet.authservice.otp.PendingPasswordResetRepository;
import com.algomeet.authservice.otp.PendingRegistrationRepository;
import com.algomeet.authservice.service.SecurityQuestionService;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

// Mockito matchers:
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.List;


import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SecurityQuestionController.class)
@AutoConfigureMockMvc(addFilters = false) // don't build SecurityFilterChain/JWT filter
@ActiveProfiles("test")
class SecurityQuestionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean SecurityQuestionService service;
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

    @Test
    void create_whenIdExists_returns400() throws Exception {
        var req = new SecurityQuestionRequest("q1", "Pet name?");
        when(service.getById("q1")).thenReturn(new SecurityQuestionResponse("q1","whatever"));

        mvc.perform(post("/auth/security-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResponseCode.SECURITY_QUESTION_ID_EXISTS.getCode()));

        verify(service, never()).create(any());
    }

    @Test
    void create_success_returns200() throws Exception {
        var req = new SecurityQuestionRequest("q9", "Custom?");
        when(service.getById("q9")).thenReturn(null);
        when(service.create(req)).thenReturn(new SecurityQuestionResponse("q9","Custom?"));

        mvc.perform(post("/auth/security-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.ADD_SECURITY_QUESTION_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value("q9"))
                .andExpect(jsonPath("$.data.question").value("Custom?"));
    }

    @Test
    void getById_ok() throws Exception {
        when(service.getById("q1")).thenReturn(new SecurityQuestionResponse("q1","Pet name?"));

        mvc.perform(get("/auth/security-questions/q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value("q1"))
                .andExpect(jsonPath("$.data.question", containsString("Pet")));
    }

    @Test
    void getAll_ok() throws Exception {
        when(service.getAll()).thenReturn(List.of(
                new SecurityQuestionResponse("q1","A"),
                new SecurityQuestionResponse("q2","B")
        ));

        mvc.perform(get("/auth/security-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value("q1"));
    }

    @Test
    void update_ok() throws Exception {
        var req = new SecurityQuestionRequest("q1","Updated?");
        when(service.update(eq("q1"), any(SecurityQuestionRequest.class)))
                .thenReturn(new SecurityQuestionResponse("q1","Updated?"));

        mvc.perform(put("/auth/security-questions/q1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.UPDATE_SECURITY_QUESTION_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.question").value("Updated?"));
    }

    @Test
    void delete_ok() throws Exception {
        doNothing().when(service).delete("q1");

        mvc.perform(delete("/auth/security-questions/q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.DELETE_SECURITY_QUESTION_SUCCESS.getCode()));

        verify(service).delete("q1");
    }


}
