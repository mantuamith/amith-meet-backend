package com.algomeet.notificationservice.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class MeetingNativeRepository {

    @PersistenceContext
    private EntityManager entityManager;
      
    /**
     * 
     * @param meeting id
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<String> getParticipantList(String meetingId) {
        String sql = """
            SELECT attendee_email
        		FROM meeting_attendees 
        		WHERE meeting_id = :meetingId
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("meetingId", meetingId);

        List<Object> results = query.getResultList();

        return results.stream()
                .map(row -> ((String) row))
                .toList();
    }
    
    /**
     * 
     * @param meeting id
     * @return
     */
    @SuppressWarnings("unchecked")
    public String getMeetingHost(String meetingId) {
        String sql = """
            SELECT host_email 
            FROM meeting
            WHERE id = :meetingId
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("meetingId", meetingId);

        Object meetingHost = query.getSingleResult();

        return (String) meetingHost;
    }
}