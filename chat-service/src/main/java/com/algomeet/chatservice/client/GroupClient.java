package com.algomeet.chatservice.client;

import com.algomeet.chatservice.document.GroupDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "group-service", url = "${group.service.url}")
public interface GroupClient {

    @GetMapping("/internal/groups/{groupId}")
    GroupDto getGroupById(@PathVariable("groupId") String groupId);

    @GetMapping("/internal/groups/member/username/{username}")
    List<GroupDto> getGroupsForUsername(@PathVariable("username") String username);

}
