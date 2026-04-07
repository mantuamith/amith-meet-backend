package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.signalservice.entity.Subscriber;
import com.algomeet.signalservice.entity.SubscriberId;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, SubscriberId> {

    List<Subscriber> findByIdUserKey(UUID userKey);

    List<Subscriber> findByIdSubscriberKey(UUID subscriberKey);

    boolean existsByIdUserKeyAndIdSubscriberKey(UUID userKey, UUID subscriberKey);

    void deleteByIdUserKeyAndIdSubscriberKey(UUID userKey, UUID subscriberKey);
}