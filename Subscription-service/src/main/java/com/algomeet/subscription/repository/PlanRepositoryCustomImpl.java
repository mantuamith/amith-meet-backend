package com.algomeet.subscription.repository;

import com.algomeet.subscription.entity.Plan;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

@Repository
@Transactional
public class PlanRepositoryCustomImpl implements PlanRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Plan insert(String code, String name, boolean active) {

        return (Plan) em.createNativeQuery("""
            INSERT INTO plans (id, code, name, is_active)
            VALUES (uuid_generate_v4(), :code, :name, :active)
            RETURNING *
        """, Plan.class)
        .setParameter("code", code)
        .setParameter("name", name)
        .setParameter("active", active)
        .getSingleResult();
    }
}
