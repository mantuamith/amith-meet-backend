package com.algomeet.signalservice.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.dto.SubscriberRequest;
import com.algomeet.signalservice.dto.SubscriberResponse;
import com.algomeet.signalservice.entity.Subscriber;
import com.algomeet.signalservice.entity.SubscriberId;
import com.algomeet.signalservice.repository.SubscriberRepository;
import com.algomeet.signalservice.service.SubscriberService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriberService {
    private final SubscriberRepository repository;

    public SubscriberResponse subscribe(SubscriberRequest request) {
    	return subscribe(request.getUserKey(), request.getSubscriberKey());
    }
    
    public SubscriberResponse subscribe(UUID userKey, UUID subscriberKey) {

        if (repository.existsByIdUserKeyAndIdSubscriberKey(
        		userKey, subscriberKey)) {
            throw new IllegalStateException("Already subscribed");
        }

        Subscriber entity = new Subscriber();
        entity.setId(new SubscriberId(
        		userKey,
        		subscriberKey
        ));

        Subscriber saved = repository.save(entity);

        return mapToResponse(saved);
    }

    public void unsubscribe(UUID userKey, UUID subscriberKey) {
        repository.deleteByIdUserKeyAndIdSubscriberKey(userKey, subscriberKey);
    }

    @Transactional(readOnly = true)
    public List<SubscriberResponse> getSubscribers(UUID userKey) {
        return repository.findByIdUserKey(userKey)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SubscriberResponse> getSubscriptions(UUID subscriberKey) {
        return repository.findByIdSubscriberKey(subscriberKey)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public boolean isSubscribed(UUID userKey, UUID subscriberKey) {
        return repository.existsByIdUserKeyAndIdSubscriberKey(userKey, subscriberKey);
    }

    private SubscriberResponse mapToResponse(Subscriber entity) {
        return SubscriberResponse.builder()
                .userKey(entity.getId().getUserKey())
                .subscriberKey(entity.getId().getSubscriberKey())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}