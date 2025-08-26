package com.algomeet.authservice.notify;

public interface SmsSender {
    void send(String to, String body);
}
