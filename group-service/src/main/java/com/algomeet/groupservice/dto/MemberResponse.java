package com.algomeet.groupservice.dto;

import com.algomeet.groupservice.enums.GroupRole;

import lombok.Data;

@Data
public class MemberResponse {
    private String userKey;
    private String username;
    private String nickname;
    private GroupRole role;
    private Long memberStartDate;
    private Long messageHistoryCutoff;
}
