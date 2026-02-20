package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserSecurityQuestionClient;
import com.algomeet.authservice.dto.UserSecurityQuestionRequest;
import com.algomeet.authservice.dto.UserSecurityQuestionResponse;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSecurityQuestionServiceTest {

    @Mock UserSecurityQuestionClient client;
    @InjectMocks UserSecurityQuestionService service;

    private static FeignException feignEx(int status) {
        Request req = Request.create(Request.HttpMethod.GET, "/x",
                Collections.emptyMap(), null, new RequestTemplate());
        return new FeignException.BadRequest("", req, null, null) {
            @Override public int status() { return status; }
            @Override public String getMessage() { return "status=" + status; }
        };
    }

    @Test
    void delete_ignores404() {
        UUID id = UUID.randomUUID();
        doThrow(feignEx(404)).when(client).deleteByUserProfileId(id);

        assertDoesNotThrow(() -> service.deleteByUserProfileId(id));
    }

    @Test
    void delete_rethrows_non404() {
        UUID id = UUID.randomUUID();
        doThrow(feignEx(500)).when(client).deleteByUserProfileId(id);

        assertThrows(FeignException.class, () -> service.deleteByUserProfileId(id));
    }

    @Test
    void getByUserProfileIdAndQuestionId_returnsNullOn404() {
        UUID id = UUID.randomUUID();
        when(client.getByUserProfileIdAndQuestionId(eq(id), eq("Q1")))
                .thenThrow(feignEx(404));

        assertNull(service.getByUserProfileIdAndQuestionId(id, "Q1"));
    }

    @Test
    void getByUserProfileIdAndQuestionId_rethrowsOn500() {
        UUID id = UUID.randomUUID();
        when(client.getByUserProfileIdAndQuestionId(eq(id), eq("Q1")))
                .thenThrow(feignEx(500));

        assertThrows(FeignException.class, () -> service.getByUserProfileIdAndQuestionId(id, "Q1"));
    }

    @Test
    void create_passThrough() {
        var req = new UserSecurityQuestionRequest();
        req.setAnswer("test");
        var body = new UserSecurityQuestionResponse();
        when(client.create(req)).thenReturn(ResponseEntity.ok(body));

        assertSame(body, service.create(req));
    }

    @Test
    void getByUserProfileId_passThrough() {
        UUID id = UUID.randomUUID();
        var list = List.of(new UserSecurityQuestionResponse());
        when(client.getByUserProfileId(id)).thenReturn(ResponseEntity.ok(list));

        assertSame(list, service.getByUserProfileId(id));
    }

    @Test
    void updateAnswer_passThrough() {
        UUID id = UUID.randomUUID();
        var req = new UserSecurityQuestionRequest();
        req.setAnswer("test");
        var body = new UserSecurityQuestionResponse();
        when(client.updateAnswer(eq(id), eq("Q1"), eq(req))).thenReturn(ResponseEntity.ok(body));

        assertSame(body, service.updateAnswer(id, "Q1", req));
    }
}
