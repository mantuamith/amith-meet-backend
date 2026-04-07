package com.algomeet.signalservice.service;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalservice.dto.E2eeEvent;
import com.algomeet.signalservice.dto.SubscriberRequest;
import com.algomeet.signalservice.dto.SubscriberResponse;
import com.algomeet.signalservice.enums.E2eeEventActionType;
import com.algomeet.signalservice.publisher.E2eeEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriberAsyncService {
    private final SubscriberService subscriberService;
    private final E2eeEventPublisher e2eeEventPublisher;

    @Async
    public CompletableFuture<Void> addAsSubscriberAsync(UUID userKey, UUID subscriberKey) {

        if (!subscriberService.isSubscribed(userKey, subscriberKey)) {
            subscriberService.subscribe(
                new SubscriberRequest(userKey, subscriberKey)
            );
        }

        return CompletableFuture.completedFuture(null);
    }
    
    
    @Async
    public CompletableFuture<Void> publishEventAsync(UUID userKey, Integer deviceId, E2eeEventActionType actionType) {
    	List<SubscriberResponse> subscribers = subscriberService.getSubscribers(userKey);
    	
    	if (!CollectionUtils.isEmpty(subscribers)) {
    		E2eeEvent event = new E2eeEvent(userKey.toString(), 
    				deviceId,
    				actionType.name().toLowerCase(),
    				subscribers.stream()
    				.map(s -> s.getSubscriberKey().toString())
    				.collect(Collectors.toSet()));
    		
    			e2eeEventPublisher.convertAndSend(userKey, event); 		
    	}
    	
    	return CompletableFuture.completedFuture(null);
    }
}