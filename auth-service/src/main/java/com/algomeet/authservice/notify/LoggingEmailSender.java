package com.algomeet.authservice.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {
    @Override
    public void send(String to, String subject, String body) {
        // For now, just log. Replace with JavaMail/Ses/etc later.
        log.info("EMAIL SEND -> to={} subject={} body={}", to, subject, body);
    }
}
