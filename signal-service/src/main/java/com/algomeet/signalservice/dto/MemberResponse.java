package com.algomeet.signalservice.dto;

import com.algomeet.signalservice.enums.GroupRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MemberResponse {
    private String userKey;
    private String username;
    private String nickname;
    private GroupRole role;
    private Long memberStartDate;
}
