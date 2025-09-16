package com.algomeet.notificationservice.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.algomeet.multitenancy.annotations.UsePublicSchema;
import com.algomeet.notificationservice.dto.UserDto;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class UserNativeRepository {

    @PersistenceContext
    private EntityManager entityManager;
   
    @SuppressWarnings("unchecked")
    @UsePublicSchema
    public List<UserDto> getUsersByUserKeyList(List<String> userKeys) {
        String placeholders = String.join(",", java.util.Collections.nCopies(userKeys.size(), "CAST(? AS uuid)"));
        String sql = String.format("SELECT id, CAST(user_key AS TEXT), username, email, device_type, device_token FROM users WHERE user_key IN (%s)", placeholders);

        Query query = entityManager.createNativeQuery(sql);

        for (int i = 0; i < userKeys.size(); i++) {
            query.setParameter(i + 1, userKeys.get(i));
        }

        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> new UserDto(
                        Long.parseLong(row[0] + ""),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5]
                ))
                .toList();
    }
    
    @SuppressWarnings("unchecked")
    @UsePublicSchema
    public List<UserDto> getUsersByUserNameList(List<String> usernames) {
        String placeholders = String.join(",", java.util.Collections.nCopies(usernames.size(), "?"));
        String sql = String.format("SELECT id, CAST(user_key AS TEXT), username, email, device_type, device_token FROM users WHERE username IN (%s)", placeholders);

        Query query = entityManager.createNativeQuery(sql);

        for (int i = 0; i < usernames.size(); i++) {
            query.setParameter(i + 1, usernames.get(i));
        }

        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> new UserDto(
                        Long.parseLong(row[0] + ""),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5]
                ))
                .toList();
    }
    
    @SuppressWarnings("unchecked")
    @UsePublicSchema
    public List<UserDto> getUsersByEmailList(List<String> emails) {
        String placeholders = String.join(",", java.util.Collections.nCopies(emails.size(), "?"));
        String sql = String.format("SELECT id, CAST(user_key AS TEXT), username, email, device_type, " + 
        		                   "device_token FROM users WHERE email IN (%s)", placeholders);

        Query query = entityManager.createNativeQuery(sql);

        for (int i = 0; i < emails.size(); i++) {
            query.setParameter(i + 1, emails.get(i));
        }

        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> new UserDto(
                        Long.parseLong(row[0] + ""),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5]
                ))
                .toList();
    }
}