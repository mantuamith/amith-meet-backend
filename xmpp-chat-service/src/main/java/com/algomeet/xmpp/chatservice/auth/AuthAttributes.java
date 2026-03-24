package com.algomeet.xmpp.chatservice.auth;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class AuthAttributes {
	public static final AttributeKey<XmppPrincipal> PRINCIPAL = AttributeKey.valueOf("xmpp.principal");
	
	public static XmppPrincipal getPrincipal(Channel channel) {
        return channel.attr(PRINCIPAL).get();
    }
}