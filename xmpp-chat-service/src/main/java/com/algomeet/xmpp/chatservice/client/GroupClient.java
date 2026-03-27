package com.algomeet.xmpp.chatservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.algomeet.xmpp.chatservice.dto.MucRoomDto;

@FeignClient(name = "group-service", url = "${group.service.url}")
public interface GroupClient {

    @GetMapping("/internal/groups/{groupId}")
    MucRoomDto getGroupById(@PathVariable("groupId") Long groupId);


}

