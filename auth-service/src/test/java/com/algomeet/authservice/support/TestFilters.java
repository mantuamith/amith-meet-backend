// src/test/java/com/algomeet/authservice/testsupport/TestFilters.java
package com.algomeet.authservice.support;

import com.algomeet.authservice.dto.SearchUsersFilter;

public final class TestFilters {
    private TestFilters() {}

    public static SearchUsersFilter filter(String username, String email, String phone,
                                           int page, int size, String sortBy, String direction, Integer tenantId) {
        SearchUsersFilter f = new SearchUsersFilter();
        f.setUsername(username);
        f.setEmail(email);
        f.setPhoneNumber(phone);
        f.setPage(page);
        f.setSize(size);
        f.setSortBy(sortBy);
        f.setDirection(direction);
        f.setTenantId(tenantId);
        return f;
    }
}
