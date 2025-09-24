package com.algomeet.userservice.repository.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.algomeet.userservice.dto.SearchUsersFilter;
import com.algomeet.userservice.model.User;

import jakarta.persistence.criteria.Predicate;


public class UserSpecification {

    public static Specification<User> filter(SearchUsersFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasLength(filter.getUsername())) {
                predicates.add(
                    cb.like(cb.lower(root.get("username")), "%" + filter.getUsername().toLowerCase() + "%")
                );
            }

            if (StringUtils.hasLength(filter.getEmail())) {
                predicates.add(
                    cb.like(cb.lower(root.get("email")), "%" + filter.getEmail().toLowerCase() + "%")
                );
            }

            if (StringUtils.hasLength(filter.getPhoneNumber())) {
                predicates.add(
                    cb.like(cb.lower(root.get("phone")), "%" + filter.getPhoneNumber().toLowerCase() + "%")
                );
            }

            if (filter.getTenantId() != null) {
                predicates.add(
                    cb.equal(root.get("tenantId"), filter.getTenantId())
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}