
package com.algomeet.authservice.util;

import com.algomeet.authservice.dto.UserProfileUpdateRequest;
import com.algomeet.authservice.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class UserProfileUpdateRequestSecuredTest {

    @Test
    void secured_nonAdmin_nullsRoleAndTenant() {
        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::isUserHasAdminRole).thenReturn(false);

            UserProfileUpdateRequest req = new UserProfileUpdateRequest();
            req.setRole("ADMIN");
            req.setTenantId(99);

            req.secured();

            assertThat(req.getRole()).isNull();
            assertThat(req.getTenantId()).isNull();
        }
    }

    @Test
    void secured_adminNotSA_nullsTenant_keepsRole_whenRoleNotSA() {
        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::isUserHasAdminRole).thenReturn(true);
            mocked.when(SecurityUtil::isSAUser).thenReturn(false);

            UserProfileUpdateRequest req = new UserProfileUpdateRequest();
            req.setRole("ADMIN");
            req.setTenantId(77);

            req.secured();

            assertThat(req.getRole()).isEqualTo("ADMIN");
            assertThat(req.getTenantId()).isNull();
        }
    }

    @Test
    void secured_adminNotSA_rejectsSettingRoleToSA() {
        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::isUserHasAdminRole).thenReturn(true);
            mocked.when(SecurityUtil::isSAUser).thenReturn(false);

            UserProfileUpdateRequest req = new UserProfileUpdateRequest();
            req.setRole("SA");

            assertThatThrownBy(req::secured)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not allowed");
        }
    }

    @Test
    void secured_SAAdmin_keepsTenant_butStillRejectsSettingRoleToSA() {
        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::isUserHasAdminRole).thenReturn(true);
            mocked.when(SecurityUtil::isSAUser).thenReturn(true);

            UserProfileUpdateRequest req = new UserProfileUpdateRequest();
            req.setTenantId(10);
            req.setRole("SA");

            assertThatThrownBy(req::secured)
                .isInstanceOf(AccessDeniedException.class);

            // If role were not SA, nothing would be nulled for SA users:
            req.setRole("ADMIN");
            req.secured();

            assertThat(req.getTenantId()).isEqualTo(10);
            assertThat(req.getRole()).isEqualTo("ADMIN");
        }
    }
}
