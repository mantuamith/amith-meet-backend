package com.algomeet.groupservice.repository;

import com.algomeet.groupservice.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByName(String name);

    List<Group> findByMembers_UserKey(String userKey);

    List<Group> findByMembers_Username(String username);
}
