package com.algomeet.xmpp.chatservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MucMember { // or ChatMember
    private String userKey;
    private String username;
    private String nickname;
    private String role;
    private boolean isMuted;
    
    private Long historyCutoffAt;
}