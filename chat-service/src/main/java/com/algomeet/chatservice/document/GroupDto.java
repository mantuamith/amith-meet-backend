package com.algomeet.chatservice.document;

import java.util.List;

import com.algomeet.chatservice.dto.Member;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupDto {
    public Long id;
    public String name;
    public List<Member> members;
}
