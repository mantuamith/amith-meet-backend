package com.algomeet.controlservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.controlservice.config.LocalizationConfig;
import com.algomeet.controlservice.controller.TenantController;
import com.algomeet.controlservice.dto.RegisterTenantRequest;
import com.algomeet.controlservice.dto.TenantRequest;
import com.algomeet.controlservice.dto.TenantResponse;
import com.algomeet.controlservice.enums.ResponseCode;
import com.algomeet.controlservice.exception.RecordNotFoundException;
import com.algomeet.controlservice.exception.TenantIdAlreadyExistsException;
import com.algomeet.controlservice.filter.JwtAuthenticationFilter;
import com.algomeet.controlservice.service.TenantService;
import com.algomeet.controlservice.util.MessageUtil;
import com.algomeet.controlservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(
    controllers = TenantController.class,
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class
        )
    }
)
@Import(LocalizationConfig.class)
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantService tenantService;

    /** REQUIRED for @PreAuthorize("@securityService.isTenantOwner(..)") */
    @MockBean(name = "securityService")
    private Object securityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    MessageSource messageSource;

    @BeforeEach
    void init() {
        new MessageUtil(messageSource);
    }

    /* -------------------------------------------------
     * GET ALL TENANTS
     * ------------------------------------------------- */

    @Test
    @WithMockUser(roles = "SA")
    void getAllTenants_success() throws Exception {
        TenantResponse t1 = TenantResponse.builder().id(1).build();

        TenantResponse t2 = TenantResponse.builder().id(2).build();

        when(tenantService.getAllTenants())
            .thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/control/tenants"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.SUCCESS.name()))
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    /* -------------------------------------------------
     * GET TENANT BY ID
     * ------------------------------------------------- */

    @Test
    @WithMockUser(roles = "SA")
    void getTenantById_success() throws Exception {
        TenantResponse resp = TenantResponse.builder().id(1).build();

        when(tenantService.getTenantById(1)).thenReturn(resp);

        mockMvc.perform(get("/control/tenants/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.SUCCESS.name()))
            .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @WithMockUser(roles = "SA")
    void getTenantById_zeroId_usesSecurityUtil() throws Exception {
        TenantResponse resp = TenantResponse.builder().id(99).build();


        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::getTenantId).thenReturn(99);
            when(tenantService.getTenantById(99)).thenReturn(resp);

            mockMvc.perform(get("/control/tenants/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                    .value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.id").value(99));
        }
    }

    @Test
    @WithMockUser(roles = "SA")
    void getTenantById_notFound() throws Exception {
        when(tenantService.getTenantById(1))
            .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/control/tenants/1"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.TENANT_ID_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * CREATE TENANT
     * ------------------------------------------------- */

    @Test
    @WithMockUser(roles = "SA")
    void createTenant_success() throws Exception {
        RegisterTenantRequest request = new RegisterTenantRequest();
        request.setId(1);
        request.setCompanyName("Tenant A");

        TenantResponse resp = TenantResponse.builder().id(1).build();

        when(tenantService.createTenant(any()))
            .thenReturn(resp);

        mockMvc.perform(post("/control/tenants")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.ADD_TENANT_SUCCESS.name()))
            .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @WithMockUser(roles = "SA")
    void createTenant_idAlreadyExists() throws Exception {
        when(tenantService.createTenant(any()))
            .thenThrow(new TenantIdAlreadyExistsException("exists"));

        RegisterTenantRequest request = new RegisterTenantRequest();
        request.setId(1);
        request.setBrandName("Tenant A");

        mockMvc.perform(post("/control/tenants")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.TENANT_ID_ALREADY_EXISTS.name()));
    }

    /* -------------------------------------------------
     * UPDATE TENANT
     * ------------------------------------------------- */

    @Test
    @WithMockUser(roles = "SA")
    void updateTenant_success() throws Exception {
        TenantRequest request = new TenantRequest();
        request.setBrandName("Tenant B");

        TenantResponse resp = TenantResponse.builder().id(1).build();

        when(tenantService.updateTenant(eq(1), any()))
            .thenReturn(resp);

        mockMvc.perform(put("/control/tenants/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.UPDATE_TENANT_SUCCESS.name()));
    }

    @Test
    @WithMockUser(roles = "SA")
    void updateTenant_notFound() throws Exception {
        when(tenantService.updateTenant(eq(1), any()))
            .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(put("/control/tenants/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.TENANT_ID_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * DELETE TENANT
     * ------------------------------------------------- */

    @Test
    @WithMockUser(roles = "SA")
    void deleteTenant_success() throws Exception {
        doNothing().when(tenantService).deleteTenant(1);

        mockMvc.perform(delete("/control/tenants/1")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.DELETE_TENANT_SUCCESS.name()));
    }

    @Test
    @WithMockUser(roles = "SA")
    void deleteTenant_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
            .when(tenantService).deleteTenant(1);

        mockMvc.perform(delete("/control/tenants/1")
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value(ResponseCode.TENANT_ID_NOT_FOUND.name()));
    }
}
