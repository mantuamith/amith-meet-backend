package com.algomeet.controlservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.algomeet.controlservice.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, Integer> {
}