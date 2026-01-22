package com.algomeet.subscription.service.admin.featureproperty;

import com.algomeet.subscription.dto.admin.featureproperty.*;
import com.algomeet.subscription.entity.Feature;
import com.algomeet.subscription.entity.FeatureProperty;
import com.algomeet.subscription.repository.FeaturePropertyRepository;
import com.algomeet.subscription.repository.FeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminFeaturePropertyService {

    private final FeatureRepository featureRepository;
    private final FeaturePropertyRepository propertyRepository;

    public AdminFeaturePropertyResponse create(AdminFeaturePropertyCreateRequest request) {

        Feature feature = featureRepository.findById(request.featureId())
                .orElseThrow(() -> new IllegalArgumentException("Feature not found"));

        if (propertyRepository.existsByFeature_IdAndPropKey(
                feature.getId(), request.propKey())) {
            throw new IllegalArgumentException("Property key already exists for this feature");
        }

        FeatureProperty prop = new FeatureProperty();
        prop.setId(UUID.randomUUID());
        prop.setFeature(feature);
        prop.setPropKey(request.propKey());
        prop.setLabel(request.label());
        prop.setValueType(request.valueType());

        return toDto(propertyRepository.save(prop));
    }

    @Transactional(readOnly = true)
    public List<AdminFeaturePropertyResponse> listByFeature(UUID featureId) {
        return propertyRepository.findByFeature_Id(featureId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AdminFeaturePropertyResponse update(
            UUID id,
            AdminFeaturePropertyUpdateRequest request
    ) {
        FeatureProperty prop = propertyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Feature property not found"));

        prop.setLabel(request.label());
        prop.setValueType(request.valueType());

        return toDto(propertyRepository.save(prop));
    }

    private AdminFeaturePropertyResponse toDto(FeatureProperty p) {
        return new AdminFeaturePropertyResponse(
                p.getId(),
                p.getFeature().getId(),
                p.getFeature().getCode(),
                p.getPropKey(),
                p.getLabel(),
                p.getValueType()
        );
    }
}
