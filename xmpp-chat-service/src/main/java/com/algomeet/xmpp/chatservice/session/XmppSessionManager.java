package com.algomeet.xmpp.chatservice.session;

import io.netty.channel.Channel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class XmppSessionManager {
    private final Map<String, Channel> sessions = new ConcurrentHashMap<>();

    public void register(String jid, Channel channel) {
        sessions.put(jid, channel);
        log.debug("Session registered for JID: {}", jid);
    }

    public void unregister(String jid) {
        sessions.remove(jid);
    }

    // REMOVE 'static'
    public Channel getChannel(String jid) {
        return sessions.get(jid);
    }

    @PreDestroy
    public void closeAll() {
        log.info("Closing {} active XMPP sessions...", sessions.size());
        sessions.values().forEach(channel -> {
            if (channel.isActive()) channel.close();
        });
        sessions.clear();
    }
}