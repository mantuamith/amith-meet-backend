package com.algomeet.meetservice.service;

import com.algomeet.meetservice.config.LinkProps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LinkFactory {


    private final LinkProps props;

    public String inviteUrl(String meetingId, String token) {
        String base = Optional.ofNullable(props.getBase()).orElse("");
        String mid  = encSafe(meetingId);
        String tokQ = encSafe(token);
        return base + "/" + mid + (tokQ.isEmpty() ? "" : "?token=" + tokQ);
    }

    private static String encSafe(String s) {
        if (s == null || s.isBlank()) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
