package com.algomeet.meetservice.repository;

import com.algomeet.meetservice.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, String> {
    List<Meeting> findAllByHostEmail(String hostEmail);
    List<Meeting> findByExpiresAtBefore(Instant instant);
}
