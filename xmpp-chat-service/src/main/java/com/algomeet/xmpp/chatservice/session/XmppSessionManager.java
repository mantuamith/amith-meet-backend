package com.algomeet.xmpp.chatservice.session;

import io.netty.channel.Channel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class XmppSessionManager {
    // Maps JID (e.g., "romeo@montague.net") to the Netty Channel
    private static final Map<String, Channel> sessions = new ConcurrentHashMap<>();

    public static void register(String jid, Channel channel) {
        sessions.put(jid, channel);
    }

    public static void unregister(String jid) {
        sessions.remove(jid);
    }

    public static Channel getChannel(String jid) {
        return sessions.get(jid);
    }
}
