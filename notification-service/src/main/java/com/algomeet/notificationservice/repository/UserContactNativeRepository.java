package com.algomeet.notificationservice.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.algomeet.notificationservice.dto.UserContactDto;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class UserContactNativeRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Todo once API is available we can use the API
     * @param username
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<UserContactDto> getUserFriendList(String username) {
        String sql = """
            SELECT u.id, u.username, u.email, u.active_device_id
            FROM contacts c
            JOIN users u ON u.username = c.contact_user_id
            WHERE c.status = 'ACCEPTED' 
              AND c.user_id = :username
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("username", username);

        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> new UserContactDto(
                        ((Number) row[0]).longValue(),  // id
                        (String) row[1],                // username
                        (String) row[2],                // email
                        (String) row[3]                 // deviceToken
                ))
                .toList();
    }
}