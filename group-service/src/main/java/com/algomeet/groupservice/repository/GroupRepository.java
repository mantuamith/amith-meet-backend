package com.algomeet.groupservice.repository;

import com.algomeet.groupservice.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    Optional<Group> findByName(String name);

    List<Group> findByMembers_UserKey(String userKey);

    List<Group> findByMembers_Username(String username);
}
