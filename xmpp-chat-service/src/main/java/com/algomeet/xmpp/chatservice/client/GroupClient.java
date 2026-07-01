package com.algomeet.xmpp.chatservice.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.common.dto.Group;

@FeignClient(name = "group-service", url = "${feign.client.group-service.url}")
public interface GroupClient {

    @GetMapping("/internal/groups/{groupId}")
    Group getGroupById(@PathVariable("groupId") String groupId);
    
    @GetMapping("/internal/groups/member/userkey/{userkey}")
	List<Group> getGroupsForUserKey(@PathVariable String userkey);
    
    /**
     * Dispatches an internal inter-service call to update a target member's timeline 
     * visibility cutoff parameters inside the remote group microservice storage cluster.
     *
     * @param groupId       The unique ID of the target group chat.
     * @param userKey       The unique user identity tracking key.
     * @param historyCutoff Optional threshold epoch time parameter window.
     * @return true if the remote database modified the target record successfully.
     */
    @PostMapping("/internal/groups/{groupId}/members/{userKey}/clear-history")
    Boolean clearMemberHistoryTimeline(
            @PathVariable("groupId") UUID groupId,
            @PathVariable("userKey") UUID userKey,
            @RequestParam(name = "historyCutoff", required = false) Long historyCutoff);
    
    
    @PostMapping("/internal/groups/{groupId}/message-retention")
    Boolean updateGroupRetention(
            @PathVariable UUID groupId,
            @RequestParam(name = "userKey") UUID targetUserKey, 
            @RequestParam(name = "messageRetentionDays") Integer messageRetentionDays);    
}

