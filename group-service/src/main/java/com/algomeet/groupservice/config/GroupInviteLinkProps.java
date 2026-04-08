package com.algomeet.groupservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class GroupInviteLinkProps {

    @Value("${algomeet.group.invite-base-url:https://yourapp.com/invite}")
    private String inviteBaseUrl;
}
