package com.algomeet.chatservice.exception;

import org.springframework.http.HttpStatus;

public class MessageEditException extends RuntimeException {
    private final HttpStatus status;

    public MessageEditException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}