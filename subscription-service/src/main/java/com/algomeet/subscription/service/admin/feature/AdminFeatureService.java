package com.algomeet.subscription.service.admin.feature;

import com.algomeet.subscription.dto.admin.feature.*;
import com.algomeet.subscription.entity.Feature;
import com.algomeet.subscription.repository.FeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminFeatureService {

    private final FeatureRepository repository;

    public AdminFeatureResponse create(AdminFeatureCreateRequest request) {

        if (repository.existsByCode(request.code())) {
            throw new IllegalArgumentException("Feature code already exists");
        }

        Feature feature = new Feature();
        feature.setId(UUID.randomUUID()); // DB-safe & explicit
        feature.setCode(request.code());
        feature.setName(request.name());
        feature.setUiGroup(request.uiGroup());
        feature.setDisplayOrder(request.displayOrder());

        return toDto(repository.save(feature));
    }

    @Transactional(readOnly = true)
    public List<AdminFeatureResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AdminFeatureResponse update(UUID id, AdminFeatureUpdateRequest request) {

        Feature feature = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found"));

        feature.setName(request.name());
        feature.setUiGroup(request.uiGroup());
        feature.setDisplayOrder(request.displayOrder());

        return toDto(repository.save(feature));
    }

    private AdminFeatureResponse toDto(Feature f) {
        return new AdminFeatureResponse(
                f.getId(),
                f.getCode(),
                f.getName(),
                f.getUiGroup(),
                f.getDisplayOrder()
        );
    }
}
