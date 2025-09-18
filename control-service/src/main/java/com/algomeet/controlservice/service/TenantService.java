package com.algomeet.controlservice.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import com.algomeet.controlservice.dto.TenantRequest;
import com.algomeet.controlservice.dto.TenantResponse;
import com.algomeet.controlservice.entity.Tenant;
import com.algomeet.controlservice.exception.TenantIdAlreadyExists;
import com.algomeet.controlservice.repository.TenantRepository;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public Optional<TenantResponse> getTenantById(Integer id) {
        return tenantRepository.findById(id).map(this::mapToResponse);
    }

    public TenantResponse createTenant(TenantRequest request) {    	
    	if (tenantRepository.findById(request.getId()).isPresent()) {
    		throw new TenantIdAlreadyExists("Tenant Id already exisit " + request.getId());
    	}
    	
        Tenant tenant = mapToEntity(request);
        Tenant saved = tenantRepository.save(tenant);
        return mapToResponse(saved);
    }

    public TenantResponse updateTenant(Integer id, TenantRequest request) {
        return tenantRepository.findById(id)
                .map(existing -> {
                    Tenant updated = mapToEntity(request);
                    updated.setId(existing.getId());
                    updated.setCreatedAt(existing.getCreatedAt());
                    return mapToResponse(tenantRepository.save(updated));
                })
                .orElseThrow(() -> new RuntimeException("Tenant not found with id " + id));
    }

    public void deleteTenant(Integer id) {
        tenantRepository.deleteById(id);
    }

    // ---------------- Mappers ----------------

    private TenantResponse mapToResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .companyName(tenant.getCompanyName())
                .brandName(tenant.getBrandName())
                .registrationNumber(tenant.getRegistrationNumber())
                .industry(tenant.getIndustry())
                .contactName(tenant.getContactName())
                .contactEmail(tenant.getContactEmail())
                .contactPhone(tenant.getContactPhone())
                .addressLine1(tenant.getAddressLine1())
                .addressLine2(tenant.getAddressLine2())
                .city(tenant.getCity())
                .stateProvince(tenant.getStateProvince())
                .postalCode(tenant.getPostalCode())
                .country(tenant.getCountry())
                .logoUrl(tenant.getLogoUrl())
                .themeColor(tenant.getThemeColor())
                .timeZone(tenant.getTimeZone())
                .active(tenant.isActive())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    private Tenant mapToEntity(TenantRequest request) {
        return Tenant.builder()
        		.id(request.getId())
                .companyName(request.getCompanyName())
                .brandName(request.getBrandName())
                .registrationNumber(request.getRegistrationNumber())
                .industry(request.getIndustry())
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .stateProvince(request.getStateProvince())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .logoUrl(request.getLogoUrl())
                .themeColor(request.getThemeColor())
                .timeZone(request.getTimeZone())
                .active(request.isActive())
                .build();
    }
}