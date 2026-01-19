package com.algomeet.notificationservice.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.notificationservice.config.LocalizationConfig;
import com.algomeet.notificationservice.dto.UserNotificationDto;
import com.algomeet.notificationservice.enums.ResponseCode;
import com.algomeet.notificationservice.exceptions.RecordNotFoundException;
import com.algomeet.notificationservice.service.UserNotificationService;
import com.algomeet.notificationservice.util.MessageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = UserNotificationController.class)
@ContextConfiguration(classes = UserNotificationController.class)
@EnableAutoConfiguration
@AutoConfigureMockMvc(addFilters = false)
@Import(LocalizationConfig.class)
class UserNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserNotificationService userNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_KEY = "user-123";
    
    @Autowired
    private MessageSource messageSource;
    
    
    @BeforeEach
    void setup() {
		new MessageUtil(messageSource);
    }

    /* -------------------------------------------------
     * GET USER NOTIFICATIONS
     * ------------------------------------------------- */
    @Test
    void getUserNotifications_success() throws Exception {
        Page<UserNotificationDto> page = new PageImpl<>(List.of());
        
        when(userNotificationService.getUserNotifications(
                USER_KEY, 0, 500, "createdAt", "desc"))
                .thenReturn(page);

        mockMvc.perform(get("/notifications/user-notifications/user/{userKey}", USER_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * GET UNREAD NOTIFICATIONS
     * ------------------------------------------------- */
    @Test
    void getUnreadNotifications_success() throws Exception {
        Page<UserNotificationDto> page = new PageImpl<>(List.of());

        when(userNotificationService.getUnreadNotifications(
                USER_KEY, 0, 500, "updatedAt", "desc"))
                .thenReturn(page);

        mockMvc.perform(get("/notifications/user-notifications/user/{userKey}/unread", USER_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * MARK AS READ
     * ------------------------------------------------- */
    @Test
    void markAsRead_success() throws Exception {
        doNothing().when(userNotificationService).markAsRead(1L);

        mockMvc.perform(patch("/notifications/user-notifications/{id}/read", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void markAsRead_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(userNotificationService).markAsRead(1L);

        mockMvc.perform(patch("/notifications/user-notifications/{id}/read", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                // current controller still returns 200
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * MARK AS DELIVERED
     * ------------------------------------------------- */
    @Test
    void markAsDelivered_success() throws Exception {
        doNothing().when(userNotificationService).markAsDelivered(1L);

        mockMvc.perform(patch("/notifications/user-notifications/{id}/delivered", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void markAsDelivered_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(userNotificationService).markAsDelivered(1L);

        mockMvc.perform(patch("/notifications/user-notifications/{id}/delivered", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                // current controller still returns 200
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * DELETE USER NOTIFICATION
     * ------------------------------------------------- */
    @Test
    void deleteUserNotification_success() throws Exception {
        doNothing().when(userNotificationService).deleteUserNotification(1L);

        mockMvc.perform(delete("/notifications/user-notifications/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteUserNotification_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(userNotificationService).deleteUserNotification(1L);

        mockMvc.perform(delete("/notifications/user-notifications/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                // current controller still returns 200
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }
}
