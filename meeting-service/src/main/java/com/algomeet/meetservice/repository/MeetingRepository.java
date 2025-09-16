package com.algomeet.meetservice.repository;

import com.algomeet.meetservice.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, String> {
    List<Meeting> findAllByHostEmail(String hostEmail);
    List<Meeting> findByExpiresAtBefore(Instant instant);
    // Find all meetings where the given email is in attendees list
    @Query("SELECT m FROM Meeting m JOIN m.attendees a WHERE a = :email ORDER BY m.meetingStartTime ASC")
    List<Meeting> findAllByAttendeeEmail(@Param("email") String email);

    List<Meeting> findByMeetingStartTimeBetween(Instant start, Instant end);
        
    @Query(value = """
    	    SELECT *
    	    FROM meeting m
    	    WHERE (m.meeting_start_time - (m.reminder_minutes * INTERVAL '1 minute'))
    	          BETWEEN :start AND :end    
    	""", nativeQuery = true)
    List<Meeting> findMeetingsByReminderTimeBetween(
    	        @Param("start") Instant start,
    	        @Param("end") Instant end);

    List<Meeting> findDistinctByHostEmailOrAttendeesContainingOrderByMeetingStartTimeAsc(String email, String email1);
}
