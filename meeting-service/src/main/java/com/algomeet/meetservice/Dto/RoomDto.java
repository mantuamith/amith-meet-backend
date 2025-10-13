package com.algomeet.meetservice.Dto;

public record RoomDto(
    String roomId,
    String roomType,        // PERSONAL / ADHOC
    String ownerEmail,
    boolean lobbyDefault,
    boolean recordingDefault
) {}
