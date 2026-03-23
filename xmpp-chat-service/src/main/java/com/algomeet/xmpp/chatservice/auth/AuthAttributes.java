package com.algomeet.xmpp.chatservice.auth;

import io.netty.util.AttributeKey;

public class AuthAttributes {
    public static final AttributeKey<String> USER_TOKEN =
            AttributeKey.valueOf("userToken");
    
    public static final AttributeKey<String> USERNAME =
            AttributeKey.valueOf("username");
}