
package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserProfileClient;
import com.algomeet.authservice.dto.UserProfileResponse;
import com.algomeet.authservice.dto.UserProfileUpdateRequest;
import com.algomeet.authservice.support.UserProfileTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    private UserProfileClient userProfileClient;
    private UserSecurityQuestionService userSecurityQuestionService;
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        userProfileClient = mock(UserProfileClient.class);
        userSecurityQuestionService = mock(UserSecurityQuestionService.class);
        service = new UserProfileService(userProfileClient, userSecurityQuestionService);
    }

    @Test
    void findById_returnsBodyFromClient() {
        UUID id = UserProfileTestData.anyProfileId();
        UserProfileResponse body = UserProfileTestData.profile(id, true);
        when(userProfileClient.getProfile(id)).thenReturn(ResponseEntity.ok(body));

        UserProfileResponse out = service.findById(id);

        assertThat(out).isSameAs(body);
        verify(userProfileClient).getProfile(id);
        verifyNoInteractions(userSecurityQuestionService);
    }

    @Test
    void updateProfile_whenRequestFlagNonNull_andResponseDisabled_triggersDelete() {
        UUID id = UserProfileTestData.anyProfileId();
        UserProfileUpdateRequest req = UserProfileTestData.updateReq(false, "ADMIN", 42);

        // updated profile returns disabled
        UserProfileResponse updated = UserProfileTestData.profile(id, false);
        when(userProfileClient.updateProfile(eq(id), any(UserProfileUpdateRequest.class)))
                .thenReturn(ResponseEntity.ok(updated));

        UserProfileResponse out = service.updateProfile(id, req);

        assertThat(out.getId()).isEqualTo(id);
        assertThat(out.getSecurityQuestionsEnabled()).isFalse();

        verify(userProfileClient).updateProfile(eq(id), eq(req));
        verify(userSecurityQuestionService).deleteByUserProfileId(id);
    }

    @Test
    void updateProfile_whenRequestFlagNull_andResponseDisabled_noDelete() {
        UUID id = UserProfileTestData.anyProfileId();
        UserProfileUpdateRequest req = UserProfileTestData.updateReq(null, "ADMIN", 42);

        UserProfileResponse updated = UserProfileTestData.profile(id, false);
        when(userProfileClient.updateProfile(eq(id), any(UserProfileUpdateRequest.class)))
                .thenReturn(ResponseEntity.ok(updated));

        UserProfileResponse out = service.updateProfile(id, req);

        assertThat(out.getSecurityQuestionsEnabled()).isFalse();
        verify(userProfileClient).updateProfile(eq(id), eq(req));
        verifyNoInteractions(userSecurityQuestionService);
    }

    @Test
    void updateProfile_whenResponseEnabled_noDelete() {
        UUID id = UserProfileTestData.anyProfileId();
        UserProfileUpdateRequest req = UserProfileTestData.updateReq(true, "ADMIN", 42);

        UserProfileResponse updated = UserProfileTestData.profile(id, true);
        when(userProfileClient.updateProfile(eq(id), any(UserProfileUpdateRequest.class)))
                .thenReturn(ResponseEntity.ok(updated));

        UserProfileResponse out = service.updateProfile(id, req);

        assertThat(out.getSecurityQuestionsEnabled()).isTrue();
        verify(userProfileClient).updateProfile(eq(id), eq(req));
        verifyNoInteractions(userSecurityQuestionService);
    }

    @Test
    void updateProfile_nulls_noDeletion() {
        UUID id = UUID.randomUUID();
        var req = new UserProfileUpdateRequest(); // securityQuestionsEnabled = null

        var resp = new UserProfileResponse();
        resp.setId(id);
        resp.setSecurityQuestionsEnabled(null);

        when(userProfileClient.updateProfile(eq(id), any())).thenReturn(ResponseEntity.ok(resp));

        service.updateProfile(id, req);

        verify(userSecurityQuestionService, never()).deleteByUserProfileId(any());
    }

    @Test
    void updateProfile_clientThrows_bubblesUp() {
        UUID id = UUID.randomUUID();
        var req = new UserProfileUpdateRequest();
        when(userProfileClient.updateProfile(eq(id), any())).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> service.updateProfile(id, req));
        verifyNoInteractions(userSecurityQuestionService);
    }

    @Test
    void updateProfile_requestTrue_noDeletion() {
        UUID id = UUID.randomUUID();

        var req = new UserProfileUpdateRequest();
        req.setSecurityQuestionsEnabled(Boolean.TRUE); // explicitly enable

        var resp = new UserProfileResponse();
        resp.setId(id);
        resp.setSecurityQuestionsEnabled(Boolean.TRUE); // service reflects enabled

        when(userProfileClient.updateProfile(eq(id), any()))
                .thenReturn(ResponseEntity.ok(resp));

        service.updateProfile(id, req);

        verify(userSecurityQuestionService, never()).deleteByUserProfileId(any());
    }

    @Test
    void updateProfile_requestFalseAndResponseFalse_triggersDeletion() {
        UUID id = UUID.randomUUID();

        var req = new UserProfileUpdateRequest();
        req.setSecurityQuestionsEnabled(Boolean.FALSE); // explicitly disable

        var resp = new UserProfileResponse();
        resp.setId(id);
        resp.setSecurityQuestionsEnabled(Boolean.FALSE); // service reflects disabled

        when(userProfileClient.updateProfile(eq(id), any()))
                .thenReturn(ResponseEntity.ok(resp));

        service.updateProfile(id, req);

        verify(userSecurityQuestionService, times(1)).deleteByUserProfileId(eq(id));
    }

    @Test
    void updateProfile_requestNull_responseFalse_noDeletion() {
        UUID id = UUID.randomUUID();

        var req = new UserProfileUpdateRequest();
        req.setSecurityQuestionsEnabled(null); // client didn’t specify

        var resp = new UserProfileResponse();
        resp.setId(id);
        resp.setSecurityQuestionsEnabled(Boolean.FALSE); // response ends up false

        when(userProfileClient.updateProfile(eq(id), any()))
                .thenReturn(ResponseEntity.ok(resp));

        service.updateProfile(id, req);

        verify(userSecurityQuestionService, never()).deleteByUserProfileId(any());
    }
}
