package com.algomeet.xmpp.chatservice.dto;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Data;


@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MucRoomDto {
    private UUID id;

    private String name;

    @JsonDeserialize(as = TreeSet.class)
    private SortedSet<MucMember> members = new TreeSet<>();    
}