package com.algomeet.controlservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.algomeet.controlservice.entity.Tenant;
import com.algomeet.multitenancy.annotations.UsePublicSchema;

public interface TenantRepository extends JpaRepository<Tenant, Integer> {
	@UsePublicSchema
	<S extends Tenant> S save(S entity);
	
	@UsePublicSchema
	Optional<Tenant> findById(Integer id);
	
	@UsePublicSchema
	List<Tenant> findAll();
	
	@UsePublicSchema
	@Query("select t.id from Tenant t where t.active = true")
    List<Integer> findActiveTenantIds();
}