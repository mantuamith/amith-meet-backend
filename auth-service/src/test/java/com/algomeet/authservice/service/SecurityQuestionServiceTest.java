package com.algomeet.authservice.service;

import com.algomeet.authservice.client.SecurityQuestionClient;
import com.algomeet.authservice.dto.SecurityQuestionRequest;
import com.algomeet.authservice.dto.SecurityQuestionResponse;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityQuestionServiceTest {

    @Mock SecurityQuestionClient client;
    @InjectMocks SecurityQuestionService svc;

    @Test
    void create_delegatesToClient() {
        var req = new SecurityQuestionRequest("q1", "Pet name?");
        var resp = new SecurityQuestionResponse("q1", "Pet name?");
        when(client.create(req)).thenReturn(ResponseEntity.ok(resp));

        var out = svc.create(req);

        assertThat(out.getId()).isEqualTo("q1");
        verify(client).create(req);
    }

    @Test
    void getById_found_returnsResponse() {
        var resp = new SecurityQuestionResponse("q2", "Mother maiden?");
        when(client.getById("q2")).thenReturn(ResponseEntity.ok(resp));

        var out = svc.getById("q2");

        assertThat(out.getQuestion()).contains("maiden");
        verify(client).getById("q2");
    }

    @Test
    void getById_notFound_returnsNull() {
        when(client.getById("missing")).thenThrow(feignNotFound());

        var out = svc.getById("missing");

        assertThat(out).isNull();
        verify(client).getById("missing");
    }

    @Test
    void getById_otherFeignError_propagates() {
        when(client.getById("boom")).thenThrow(feignBadRequest());

        assertThatThrownBy(() -> svc.getById("boom"))
                .isInstanceOf(FeignException.class);
        verify(client).getById("boom");
    }

    @Test
    void getAll_empty_populatesDefaults_thenReturnsFreshList() {
        // consecutive returns: first empty, then seeded list
        var seeded = List.of(
                new SecurityQuestionResponse("q1", "What is your pet's name?"),
                new SecurityQuestionResponse("q2", "What is your mother’s maiden name?"),
                new SecurityQuestionResponse("q3", "What was your first school?"),
                new SecurityQuestionResponse("q4", "What is your favorite color?"),
                new SecurityQuestionResponse("q5", "What is your birthplace?")
        );
        when(client.getAll())
                .thenReturn(ResponseEntity.ok(Collections.emptyList()))
                .thenReturn(ResponseEntity.ok(seeded));

        when(client.create(any(SecurityQuestionRequest.class)))
                .thenAnswer(inv -> {
                    var r = (SecurityQuestionRequest) inv.getArgument(0);
                    return ResponseEntity.ok(new SecurityQuestionResponse(r.getId(), r.getQuestion()));
                });

        var out = svc.getAll();

        assertThat(out).hasSize(5);

        var cap = ArgumentCaptor.forClass(SecurityQuestionRequest.class);
        verify(client, times(5)).create(cap.capture());
        assertThat(cap.getAllValues().stream().map(SecurityQuestionRequest::getId))
                .containsExactlyInAnyOrder("q1","q2","q3","q4","q5");

        verify(client, times(2)).getAll();
    }

    @Test
    void getAll_nonEmpty_returnsAsIs_noSeeding() {
        var existing = List.of(
                new SecurityQuestionResponse("a","A?"),
                new SecurityQuestionResponse("b","B?")
        );
        when(client.getAll()).thenReturn(ResponseEntity.ok(existing));

        var out = svc.getAll();

        assertThat(out).containsExactlyElementsOf(existing);
        verify(client, times(1)).getAll();
        verify(client, never()).create(any());
    }

    @Test
    void getAll_empty_seedPartiallyStillReturnsSecondFetch_withoutThrow() {
        when(client.getAll())
                .thenReturn(ResponseEntity.ok(Collections.emptyList()))
                .thenReturn(ResponseEntity.ok(List.of(
                        new SecurityQuestionResponse("q1","x"),
                        new SecurityQuestionResponse("q2","y")
                )));
        // first create ok, second returns 500 (no exception)
        when(client.create(any(SecurityQuestionRequest.class)))
                .thenReturn(ResponseEntity.ok(new SecurityQuestionResponse("q1","x")))
                .thenReturn(ResponseEntity.internalServerError().build());

        var out = svc.getAll();

        assertThat(out).hasSize(2);
        verify(client, atLeastOnce()).create(any());
        verify(client, times(2)).getAll();
    }


    @Test
    void update_delegatesToClient() {
        var req = new SecurityQuestionRequest("q1","Updated?");
        var resp = new SecurityQuestionResponse("q1","Updated?");
        when(client.update("q1", req)).thenReturn(ResponseEntity.ok(resp));

        var out = svc.update("q1", req);

        assertThat(out.getQuestion()).isEqualTo("Updated?");
        verify(client).update("q1", req);
    }

    @Test
    void delete_delegatesToClient() {
        doReturn(ResponseEntity.noContent().build()).when(client).delete("q9");

        svc.delete("q9");

        verify(client).delete("q9");
    }


    private static FeignException.NotFound feignNotFound() {
        return new FeignException.NotFound(
                "404",
                Request.create(Request.HttpMethod.GET, "/internal/security-questions/missing",
                        Map.of(), null, StandardCharsets.UTF_8, null),
                null, Map.of());
    }

    private static FeignException.BadRequest feignBadRequest() {
        return new FeignException.BadRequest(
                "400",
                Request.create(Request.HttpMethod.GET, "/internal/security-questions/boom",
                        Map.of(), null, StandardCharsets.UTF_8, null),
                null, Map.of());
    }
}
