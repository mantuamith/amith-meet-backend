package com.algomeet.xmpp.chatservice.auth;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.UserSession;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.XmppSessionManager;
import com.algomeet.xmpp.chatservice.stanza.BindResult;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketPostAuthHandler extends ChannelInboundHandlerAdapter {
    private final UserSessionRegistry userSessionRegistry;
    private final XmppSessionManager xmppSessionManager;
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
            
            if (principal != null) {
                String userKey = principal.getUserKey();
                String sessionId = principal.getSessionId();
               
                // 1. Initialize Stream Management Counter (XEP-0198)
                ctx.channel().attr(XmppSessionAttributes.HANDLED_COUNT_KEY).set(new AtomicLong(0));

                // 2. Register in Managers (Only now that the WS is stable)
                xmppSessionManager.register(userKey, ctx.channel());
                userSessionRegistry.addSession(userKey, new UserSession(sessionId, UserState.ACTIVE, Instant.now().toEpochMilli()));
                
                // 3. Setup Close Listener
                ctx.channel().closeFuture().addListener((ChannelFutureListener) future -> {
                    xmppSessionManager.unregister(userKey);
                    userSessionRegistry.removeSession(userKey, sessionId);
                    log.info("Session {} for user {} cleaned up.", sessionId, userKey);
                });

                // Send channel/session ID to client
                ctx.channel().writeAndFlush(new TextWebSocketFrame(new BindResult(principal.getFullJid(), sessionId).toXml()));
                log.info("WebSocket Handshake Complete. User {} is now ACTIVE.", userKey);
            }
        }
        
        super.userEventTriggered(ctx, evt);
    }
}