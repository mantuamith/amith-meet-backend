package com.algomeet.notificationservice.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class UserContactNativeRepository {

    @PersistenceContext
    private EntityManager entityManager;
   
    /**
     * 
     * @param userKey
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserFriendListByUserKey(String userKey) {
        String sql = """
            SELECT CAST(contact_user_key AS TEXT)
            FROM contacts 
            WHERE status = 'ACCEPTED' 
              AND user_key = CAST(:userKey AS UUID)
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userKey", userKey);

        List<Object> results = query.getResultList();

        return results.stream()
                .map(row -> ((String) row))
                .toList();
    }
    
    /**
     * 
     * @param username
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserFriendListByUsername(String username) {
        String sql = """
            SELECT CAST(contact_user_key AS TEXT)
            FROM contacts 
            WHERE status = 'ACCEPTED' 
              AND user_id = :username
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("username", username);

        List<Object> results = query.getResultList();

        return results.stream()
                .map(row -> ((String) row))
                .toList();
    }
}