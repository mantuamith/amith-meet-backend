package com.algomeet.chatservice.mapper;

import org.springframework.stereotype.Component;

import com.algomeet.chatservice.document.GroupSessionMessageDocument;
import com.algomeet.chatservice.document.GroupSessionMessageResponse;

@Component
public class GroupSessionMessageMapper {    
    public GroupSessionMessageResponse toResponse(GroupSessionMessageDocument document) {
    	return GroupSessionMessageResponse.builder()
    			.id(document.getId())
    			.type(document.getType())
    			.groupId(document.getGroupId())
    			.to(document.getTo())
    			.toKey(document.getToKey())
    			.from(document.getFrom())
    			.fromKey(document.getFromKey())
    			.payload(document.getPayload())
    			.build();		
    }
}
