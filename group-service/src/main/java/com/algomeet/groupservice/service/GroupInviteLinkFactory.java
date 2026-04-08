package com.algomeet.groupservice.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.algomeet.groupservice.config.GroupInviteLinkProps;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GroupInviteLinkFactory {

    private final GroupInviteLinkProps props;

    public String build(Long groupId, String inviteCode) {
        return UriComponentsBuilder
                .fromUriString(props.getInviteBaseUrl())
                .queryParam("groupId", groupId)
                .queryParam("inviteCode", inviteCode)
                .build()
                .toUriString();
    }
}
