package com.algomeet.xmpp.chatservice.auth;

import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.UserSession;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionManager;
import com.algomeet.xmpp.chatservice.util.JwtUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final JwtUtil jwtUtil;
    private final UserSessionRegistry userSessionRegistry;
    private final XmppSessionManager xmppSessionManager;
    
    @Value("${xmpp.domain}")
    private String domain;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String token = extractToken(request);

        // 1. Validate Token
        if (!StringUtils.hasText(token) || !jwtUtil.validate(token)) {
            log.warn("Unauthorized access attempt to URI: {}", request.uri());
            sendUnauthorized(ctx);
            return;
        }

        // 2. Extract Identity
        String userKey = jwtUtil.getUserKey(token);
        if (!StringUtils.hasText(userKey)) {
            log.error("Token valid but UserKey missing for URI: {}", request.uri());
            sendUnauthorized(ctx);
            return;
        }

        // 3. Build and Store Principal in Channel Attribute
        String sessionId = ctx.channel().id().asLongText();
        XmppPrincipal principal = XmppPrincipal.builder()
                .userKey(userKey)
                .username(jwtUtil.getUsername(token))
                .tenantId(jwtUtil.getTenantId(token))
                .sessionId(sessionId)
                .domain(domain)
                .build();

        ctx.channel().attr(AuthAttributes.PRINCIPAL).set(principal);

        // 4. Register Session in Managers
        xmppSessionManager.register(userKey, ctx.channel());
        userSessionRegistry.addSession(userKey, new UserSession(sessionId, UserState.ACTIVE, Instant.now().toEpochMilli()));
        
        log.info("User {} authenticated. Session: {}", userKey, sessionId);

        // 5. Cleanup: Listener for disconnection
        ctx.channel().closeFuture().addListener((ChannelFutureListener) future -> {
            xmppSessionManager.unregister(userKey);
            userSessionRegistry.removeSession(userKey, sessionId);
            log.info("User {} disconnected, session {} removed.", userKey, sessionId);
        });

        // 6. Continue to WebSocket Handshake (No 'CONNECTED' frame sent)
        ctx.fireChannelRead(request.retain());
    }
    
    /**
     * Sends the Session ID to the client once the WebSocket Handshake is finished.
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            XmppPrincipal principal = ctx.channel().attr(AuthAttributes.PRINCIPAL).get();
            
            if (principal != null) {
                // Standard XMPP format for a session bind result
                String sessionId = principal.getSessionId();
                String jid = principal.getFullJid();

                String xmppResponse = String.format(
                    "<iq type='result' id='bind_1'>" +
                    "  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>" +
                    "    <jid>%s</jid>" +
                    "    <sessionid>%s</sessionid>" +
                    "  </bind>" +
                    "</iq>",
                    jid, sessionId
                );

                // Send as a TextWebSocketFrame
                ctx.channel().writeAndFlush(new TextWebSocketFrame(xmppResponse));
                
                log.info("XMPP Session Bound. JID: {} sent to client.", jid);
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    private String extractToken(FullHttpRequest request) {
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(Constants.BEARER_PREFIX)) {
            return authHeader.substring(Constants.BEARER_PREFIX.length());
        }
        return extractTokenFromQuery(request.uri());
    }

    private String extractTokenFromQuery(String uri) {
        int queryStart = uri.indexOf('?');
        if (queryStart == -1) return null;

        String query = uri.substring(queryStart + 1);
        return Arrays.stream(query.split("&"))
                .filter(p -> p.startsWith(Constants.TOKEN_PARAM))
                .map(p -> p.substring(Constants.TOKEN_PARAM.length()))
                .map(v -> URLDecoder.decode(v, StandardCharsets.UTF_8))
                .findFirst()
                .orElse(null);
    }

    private void sendUnauthorized(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED,
                Unpooled.copiedBuffer("Unauthorized", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer");
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}