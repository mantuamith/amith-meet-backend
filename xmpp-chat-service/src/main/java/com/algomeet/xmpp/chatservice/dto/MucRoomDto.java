package com.algomeet.xmpp.chatservice.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data 
public class MucRoomDto {
    private String id; // Changed from Long id to String to match XMPP JIDs
    private String name;
    private List<MucMember> members;
}