package com.algomeet.controlservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;

import com.algomeet.controlservice.dto.TenantRequest;
import com.algomeet.controlservice.dto.TenantResponse;
import com.algomeet.controlservice.entity.Tenant;
import com.algomeet.controlservice.exception.RecordNotFoundException;
import com.algomeet.controlservice.exception.TenantIdAlreadyExistsException;
import com.algomeet.controlservice.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantService tenantService;

    private Tenant tenant;
    private TenantRequest tenantRequest;

    @BeforeEach
    void setUp() {
        tenantRequest = new TenantRequest();
        tenantRequest.setId(1);
        tenantRequest.setCompanyName("Company");
        tenantRequest.setBrandName("Brand");
        tenantRequest.setRegistrationNumber("12345");
        tenantRequest.setIndustry("IT");
        tenantRequest.setContactName("Alice");
        tenantRequest.setContactEmail("alice@example.com");
        tenantRequest.setContactPhone("123456789");
        tenantRequest.setAddressLine1("Address 1");
        tenantRequest.setAddressLine2("Address 2");
        tenantRequest.setCity("City");
        tenantRequest.setStateProvince("State");
        tenantRequest.setPostalCode("12345");
        tenantRequest.setCountry("Country");
        tenantRequest.setLogoUrl("http://logo.url");
        tenantRequest.setThemeColor("blue");
        tenantRequest.setTimeZone("UTC");
        tenantRequest.setActive(true);


        tenant = Tenant.builder()
                .id(1)
                .companyName("Company")
                .brandName("Brand")
                .registrationNumber("12345")
                .industry("IT")
                .contactName("Alice")
                .contactEmail("alice@example.com")
                .contactPhone("123456789")
                .addressLine1("Address 1")
                .addressLine2("Address 2")
                .city("City")
                .stateProvince("State")
                .postalCode("12345")
                .country("Country")
                .logoUrl("http://logo.url")
                .themeColor("blue")
                .timeZone("UTC")
                .active(true)
                .build();
    }

    /* -------------------------------------------------
     * GET ALL TENANTS
     * ------------------------------------------------- */

    @Test
    void getAllTenants_success() {
        Tenant tenant2 = Tenant.builder().id(2).companyName("Other").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant, tenant2));

        List<TenantResponse> responses = tenantService.getAllTenants();

        assertEquals(2, responses.size());
        assertEquals("Company", responses.get(0).getCompanyName());
        assertEquals("Other", responses.get(1).getCompanyName());
    }

    /* -------------------------------------------------
     * GET TENANT BY ID
     * ------------------------------------------------- */

    @Test
    void getTenantById_success() {
        when(tenantRepository.findById(1)).thenReturn(Optional.of(tenant));

        TenantResponse response = tenantService.getTenantById(1);

        assertNotNull(response);
        assertEquals("Company", response.getCompanyName());
    }

    @Test
    void getTenantById_notFound() {
        when(tenantRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(
            RecordNotFoundException.class,
            () -> tenantService.getTenantById(1)
        );
    }

    /* -------------------------------------------------
     * CREATE TENANT
     * ------------------------------------------------- */

    @Test
    void createTenant_success() {
        when(tenantRepository.findById(1)).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

        TenantResponse response = tenantService.createTenant(tenantRequest);

        assertNotNull(response);
        assertEquals("Company", response.getCompanyName());
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void createTenant_idAlreadyExists() {
        when(tenantRepository.findById(1)).thenReturn(Optional.of(tenant));

        assertThrows(
            TenantIdAlreadyExistsException.class,
            () -> tenantService.createTenant(tenantRequest)
        );

        verify(tenantRepository, never()).save(any());
    }

    /* -------------------------------------------------
     * UPDATE TENANT
     * ------------------------------------------------- */

    @Test
    void updateTenant_success() {
    	// Copy properties from source to target
    	Tenant tenantResp = new Tenant(); 
        BeanUtils.copyProperties(tenant, tenantResp);
        tenantResp.setCompanyName("Updated Company");
        
        when(tenantRepository.findById(1)).thenReturn(Optional.of(tenantResp));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenantResp);

        tenantRequest.setCompanyName("Updated Company");
        TenantResponse response = tenantService.updateTenant(1, tenantRequest);

        assertEquals("Updated Company", response.getCompanyName());
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void updateTenant_notFound() {
        when(tenantRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(
            RecordNotFoundException.class,
            () -> tenantService.updateTenant(1, tenantRequest)
        );

        verify(tenantRepository, never()).save(any());
    }

    /* -------------------------------------------------
     * DELETE TENANT
     * ------------------------------------------------- */

    @Test
    void deleteTenant_success() {
        when(tenantRepository.findById(1)).thenReturn(Optional.of(tenant));

        tenantService.deleteTenant(1);

        verify(tenantRepository).deleteById(1);
    }

    @Test
    void deleteTenant_notFound() {
        when(tenantRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(
            RecordNotFoundException.class,
            () -> tenantService.deleteTenant(1)
        );

        verify(tenantRepository, never()).deleteById(any());
    }

    /* -------------------------------------------------
     * GET ACTIVE TENANT IDS
     * ------------------------------------------------- */

    @Test
    void getActiveTenantIds_success() {
        when(tenantRepository.findActiveTenantIds()).thenReturn(List.of(1, 2, 3));

        List<Integer> ids = tenantService.getActiveTenantIds();

        assertEquals(3, ids.size());
        assertEquals(1, ids.get(0));
        assertEquals(3, ids.get(2));
    }
}
