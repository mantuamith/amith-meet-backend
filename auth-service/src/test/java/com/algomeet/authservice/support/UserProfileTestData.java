package com.algomeet.authservice.support;

import com.algomeet.authservice.dto.UserProfileResponse;
import com.algomeet.authservice.dto.UserProfileUpdateRequest;

import java.math.BigDecimal;
import java.util.UUID;

public final class UserProfileTestData {
    private UserProfileTestData() {}

    public static UUID anyProfileId() {
        return UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    public static UserProfileUpdateRequest updateReq(
            Boolean securityQuestionsEnabled,
            String role,
            Integer tenantId
    ) {
        UserProfileUpdateRequest req = new UserProfileUpdateRequest();
        req.setLoginTypePolicy((short) 1);
        req.setCountry("US");
        req.setRegion("CA");
        req.setCity("San Francisco");
        req.setLatitude(new BigDecimal("37.7749"));
        req.setLongitude(new BigDecimal("-122.4194"));
        req.setRegistrationDeviceId("dev-123");
        req.setRegistrationDeviceType("ios");
        req.setPasscode("123456");
        req.setSecurityQuestionsEnabled(securityQuestionsEnabled);
        req.setRole(role);
        req.setTenantId(tenantId);
        return req;
    }

    public static UserProfileUpdateRequest updateReq(Boolean securityQuestionsEnabled) {
        return updateReq(securityQuestionsEnabled, null, null);
    }

    public static UserProfileResponse profile(UUID id, Boolean securityQuestionsEnabled) {
        UserProfileResponse resp = new UserProfileResponse();
        resp.setId(id);
        resp.setSecurityQuestionsEnabled(securityQuestionsEnabled);
        return resp;
    }
}
