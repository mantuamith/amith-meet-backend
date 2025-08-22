package com.algomeet.authservice.notify;

public interface EmailSender {
    void send(String to, String subject, String body);
}
