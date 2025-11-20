package com.algomeet.meetservice.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class OpenMeetingJoinResponse {
    private MeetingDto meeting;     // <-- DTO instead of entity
    private String algomeetToken;
    private String room;
    private Instant tokenExpiresAt;
}
