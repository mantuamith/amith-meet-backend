package com.algomeet.xmpp.chatservice.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.concurrent.atomic.AtomicLong;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;

public class XmppSessionAttributes {
	public static final AttributeKey<XmppPrincipal> PRINCIPAL = AttributeKey.valueOf("xmpp.principal");	

	// Define a key to store the counter in the Netty Channel context
	public static final AttributeKey<AtomicLong> HANDLED_COUNT_KEY = AttributeKey.valueOf("handledCount");
	
	public static XmppPrincipal getPrincipal(Channel channel) {
        return channel.attr(PRINCIPAL).get();
    }
}