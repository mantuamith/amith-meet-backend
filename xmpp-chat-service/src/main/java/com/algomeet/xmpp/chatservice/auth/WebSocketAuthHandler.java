package com.algomeet.xmpp.chatservice.auth;

import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.UserSession;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
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
import java.util.concurrent.atomic.AtomicLong;

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

        // 1. Validate Token (Pre-Handshake)
        if (!StringUtils.hasText(token) || !jwtUtil.validate(token)) {
            log.warn("Unauthorized HTTP attempt: {}", request.uri());
            sendUnauthorized(ctx);
            return;
        }

        String userKey = jwtUtil.getUserKey(token);
        
        // 2. Prepare Principal (But don't register yet!)
        XmppPrincipal principal = XmppPrincipal.builder()
                .userKey(userKey)
                .username(jwtUtil.getUsername(token))
                .tenantId(jwtUtil.getTenantId(token))
                .sessionId(ctx.channel().id().asLongText())
                .domain(domain)
                .build();

        // Store in attribute so userEventTriggered can see it
        ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).set(principal);     
      
        // 3. Pass to WebSocketServerProtocolHandler
        ctx.fireChannelRead(request.retain());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
            
            if (principal != null) {
                String userKey = principal.getUserKey();
                String sessionId = principal.getSessionId();

                // --- REGISTRATION LOGIC START ---
                
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
                // --- REGISTRATION LOGIC END ---

                // Send Bind Result
                String xmppResponse = String.format(
                    "<iq type='result' id='bind_1'>" +
                    "  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>" +
                    "    <jid>%s</jid>" +
                    "    <sessionid>%s</sessionid>" +
                    "  </bind>" +
                    "</iq>",
                    principal.getFullJid(), sessionId
                );

                ctx.channel().writeAndFlush(new TextWebSocketFrame(xmppResponse));
                log.info("WebSocket Handshake Complete. User {} is now ACTIVE.", userKey);
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