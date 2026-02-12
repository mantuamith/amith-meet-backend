package com.algomeet.mediaservice.repository;

import com.algomeet.mediaservice.entity.UserStorageUsage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface UserStorageUsageRepository extends JpaRepository<UserStorageUsage, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserStorageUsage u WHERE u.userKey = :userKey")
    Optional<UserStorageUsage> findByUserKeyForUpdate(@Param("userKey") UUID userKey);
}
