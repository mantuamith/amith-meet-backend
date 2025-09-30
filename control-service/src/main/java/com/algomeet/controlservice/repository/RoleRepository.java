package com.algomeet.controlservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.algomeet.controlservice.entity.Role;
import com.algomeet.multitenancy.annotations.UsePublicSchema;

public interface RoleRepository extends JpaRepository<Role, String> {
	@UsePublicSchema
	<S extends Role> S save(S entity);
	
	@UsePublicSchema
	Optional<Role> findById(String id);
	
	@UsePublicSchema
	List<Role> findAll();
	
	@UsePublicSchema
	void deleteById(String id);
	
	@UsePublicSchema
	boolean existsById(String id);
	
	@UsePublicSchema
    boolean existsByNameIgnoreCase(String name);
}