package com.algomeet.authservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorBody {
    private int status;
    private String error;     // e.g. "Bad Request", "ValidationError", "Unauthorized"
    private String message;   // human-readable detail
    private String path;      // request URI
    private String method;    // HTTP method

    @JsonFormat(shape = JsonFormat.Shape.STRING) // explicit ISO-8601
    private Instant timestamp;

    // ---- Convenience constructors ----
    public static ErrorBody of(int status, String error, String message) {
        return ErrorBody.builder()
                .status(status)
                .error(error)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorBody of(int status, String error, String message, String path, String method) {
        return ErrorBody.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .method(method)
                .timestamp(Instant.now())
                .build();
    }
}
