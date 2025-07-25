package com.algomeet.chatservice.client;

import com.algomeet.chatservice.dto.GroupDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "group-service", url = "${group.service.url}")
public interface GroupClient {

    @GetMapping("/internal/groups/{groupId}")
    GroupDto getGroupById(@PathVariable("groupId") Long groupId);


}

