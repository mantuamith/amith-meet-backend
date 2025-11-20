package com.algomeet.meetservice.exception;

import com.algomeet.meetservice.Dto.MeetingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MeetingResponse handleBadRequest(RuntimeException ex) {
        return MeetingResponse.error("MEETING_BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public MeetingResponse handleForbidden(AccessDeniedException ex) {
        return MeetingResponse.error("MEETING_ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MeetingResponse handleValidation(MethodArgumentNotValidException ex) {
        return MeetingResponse.error("VALIDATION_ERROR", "Invalid request payload");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public MeetingResponse<?> handleFallback(Exception ex) {
        ex.printStackTrace(); // TEMP: helps you see exact class & line of s.length()
        return MeetingResponse.error("INTERNAL_ERROR", "Unexpected error");
    }
}
