package com.algomeet.groupservice.repository;

import com.algomeet.groupservice.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    Optional<Group> findByName(String name);

    List<Group> findByMembers_UserKey(String userKey);

    List<Group> findByMembers_Username(String username);
    
    /**
     * Updates a single member's history cutoff directly in the collection table.
     * Bypasses the Hibernate entity lifecycle to prevent concurrency race conditions.
     *
     * @param groupId  The group ID matching the 'group_id' foreign key column
     * @param userKey  The user identifier matching the 'user_key' column
     * @param cutoff   The new millisecond history cutoff timestamp
     * @return The number of rows modified (should be 1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE chat_group_members " +
                   "SET message_history_cutoff = :cutoff " +
                   "WHERE group_id = :groupId AND user_key = :userKey", 
           nativeQuery = true)
    int updateSingleMemberHistoryCutoff(@Param("groupId") UUID groupId, 
                                        @Param("userKey") String userKey, 
                                        @Param("cutoff") Long cutoff);
    
    
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE chat_groups " +
                   "SET message_retention_days = :messageRetentionDays " +
                   "WHERE id = :groupId", 
           nativeQuery = true)
    int updateGroupRetention(@Param("groupId") UUID groupId, 
                             @Param("messageRetentionDays") Integer messageRetentionDays);
}
