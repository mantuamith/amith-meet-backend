package com.algomeet.signalservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.algomeet.signalservice.dto.GroupResponse;


@FeignClient(name = "group-service", url = "${feign.client.group-service.url}")
public interface GroupClient {

    @GetMapping("/internal/groups/{groupId}")
    GroupResponse getGroupById(@PathVariable("groupId") String groupId);
    
    @GetMapping("/internal/groups/member/userkey/{userkey}")
	List<GroupResponse> getGroupsForUserKey(@PathVariable String userkey);


}

