package com.algomeet.meetservice.Dto;

import java.time.Instant;
import java.util.List;

public record MeetingDto(
    String id,
    String meetingType,
    String hostEmail,
    String status,
    String token,
    Instant meetingStartTime,
    Instant meetingEndTime,
    RoomDto room,
    String hostName,
    String meetingName,
    String meetingDescription,
    boolean lobbyEnabled,
    boolean reminderEnabled,
    int reminderMinutes,
    List<String> attendees,
    List<String> invitedParticipants,
    String joinUrl,
    Boolean passwordEnabled,
    String passwordHash
) {}
