package com.algomeet.meetservice.Dto;

public record OpenJoinRequest(
        String token,
        String password,
        String moderatorPassword,
        String name,
        String userKey
) {
}
