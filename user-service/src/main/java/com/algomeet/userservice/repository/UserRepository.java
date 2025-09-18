package com.algomeet.userservice.repository;

import com.algomeet.multitenancy.annotations.UsePublicSchema;
import com.algomeet.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface UserRepository extends JpaRepository<User, Long> {
	@UsePublicSchema
	<S extends User> S save(S entity);
	
	@UsePublicSchema
    Optional<User> findByUsername(String username);

	@UsePublicSchema
    Optional<User> findByEmail(String email);

	@UsePublicSchema
    List<User> findAllByEmailIn(List<String> emails);

	@UsePublicSchema
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchUsers(@Param("query") String query);

    //boolean deleteUserByEmail(String email);

	@UsePublicSchema
    boolean existsByEmail(String email);

	@UsePublicSchema
    void deleteByEmail(String email);

	@UsePublicSchema
    boolean existsByUsername(String username);

	@UsePublicSchema
    boolean existsByPhone(String phone);

	@UsePublicSchema
    Optional<User> findByUsernameIgnoreCase(String username);
	
	@UsePublicSchema
    Optional<User> findByEmailIgnoreCase(String email);
    
    @UsePublicSchema
    Optional<User> findByUserKey(UUID key);

    @UsePublicSchema
    List<User> findAllByUserKeyIn(List<UUID> keys);

    @UsePublicSchema
    List<User> findByUserKeyIn(Collection<UUID> keys);
}
