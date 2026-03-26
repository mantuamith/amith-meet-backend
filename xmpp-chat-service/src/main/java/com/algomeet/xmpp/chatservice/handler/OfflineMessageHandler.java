package com.algomeet.xmpp.chatservice.handler;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OfflineMessageHandler {
    private final OfflineMessageService offlineMessageService;
    
    @Value("${xmpp.server.domain}")
    private String domain;

    public void deliverOfflineMessages(ChannelHandlerContext ctx, XmppPrincipal principal) {
        List<OfflineMessage> messages = offlineMessageService.getOfflineMessages(principal.getUserKey());
        
        for (OfflineMessage msg : messages) {
            String xmlWithDelay = wrapWithDelay(msg.getStanzaXml(), msg.getCreatedAt(), principal);
            ctx.writeAndFlush(new TextWebSocketFrame(xmlWithDelay));
        }
        
        // Optional: Clear the inbox after sending
        // offlineMessageService.clear(principal.getUserKey());
    }

    private String wrapWithDelay(String originalXml, Instant timestamp, XmppPrincipal principal) {
        // According to XEP-0203, 'from' should be the server domain
        String delay = String.format(
            "<delay xmlns='urn:xmpp:delay' from='%s' stamp='%s'/>",
            domain, timestamp.toString()
        );
        
        // Robust insertion: insert before the closing tag of the top-level element
        int lastIndex = originalXml.lastIndexOf("</");
        if (lastIndex != -1) {
            return originalXml.substring(0, lastIndex) + delay + originalXml.substring(lastIndex);
        }
        return originalXml + delay;
    }
}