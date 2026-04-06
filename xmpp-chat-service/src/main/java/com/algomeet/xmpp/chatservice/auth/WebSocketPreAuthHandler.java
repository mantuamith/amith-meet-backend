package com.algomeet.xmpp.chatservice.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.JwtUtil;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Handles pre-handshake authentication for WebSocket connections using JWT.</p>
 * * <p>This handler intercepts the initial {@link FullHttpRequest} (HTTP GET with Upgrade header) 
 * before the Netty {@code WebSocketServerProtocolHandler} performs the handshake. 
 * It ensures that only authenticated clients can establish a long-lived session.</p>
 * * <p><b>Authentication Methods:</b></p>
 * <ul>
 * <li><b>Standard Header:</b> Looks for the {@code Authorization: Bearer <token>} header.</li>
 * <li><b>Query Parameter:</b> Fallback to {@code ?token=<token>} for clients (like some web browsers) 
 * that cannot easily set custom headers during a WebSocket constructor call.</li>
 * </ul>
 * * <p>If authentication succeeds, an {@link XmppPrincipal} is attached to the channel's 
 * attributes for use by subsequent handlers in the pipeline. If it fails, a 401 Unauthorized 
 * response is returned and the connection is closed.</p>
 * * @author Algomeet Core Team
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketPreAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final JwtUtil jwtUtil;
    
    @Value("${xmpp.server.domain}")
    private String domain;
    
    /**
     * Intercepts the HTTP request to perform JWT validation.
     * * @param ctx     The Netty channel context.
     * @param request The full HTTP request attempting to upgrade to WebSocket.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String token = extractToken(request);

        // 1. Validate Token (Pre-Handshake)
        if (!StringUtils.hasText(token) || !jwtUtil.validate(token)) {
            log.warn("Unauthorized HTTP/WS upgrade attempt from IP: {} for URI: {}", 
                ctx.channel().remoteAddress(), request.uri());
            sendUnauthorized(ctx);
            return;
        }

        // 2. Extract Identity & Build Principal
        String userKey = jwtUtil.getUserKey(token);
        XmppPrincipal principal = XmppPrincipal.builder()
                .userKey(userKey)                
                .username(jwtUtil.getUsername(token))
                .tenantId(jwtUtil.getTenantId(token))
                .sessionId(ctx.channel().id().asLongText()) // Unique ID for this physical connection
                .domain(domain)
                .build();

        // Store the principal in a Channel Attribute. 
        // This is retrieved later in WebSocketPostAuthHandler after the handshake completes.
        ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).set(principal);     
      
        // 3. Hand over the request to the next handler (WebSocketServerProtocolHandler)
        // retain() is called because SimpleChannelInboundHandler would otherwise release the buffer.
        ctx.fireChannelRead(request.retain());		
    }
	
    /**
     * Attempts to extract the JWT from the Authorization header or URL query parameters.
     */
    private String extractToken(FullHttpRequest request) {
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(Constants.BEARER_PREFIX)) {
            return authHeader.substring(Constants.BEARER_PREFIX.length());
        }
        return extractTokenFromQuery(request.uri());
    }

    /**
     * Parses the URI to find a 'token' parameter. Useful for browser-based XMPP clients.
     */
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

    /**
     * Rejects the connection with an HTTP 401 response and closes the channel.
     */
    private void sendUnauthorized(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED,
                Unpooled.copiedBuffer("Unauthorized: Valid JWT required", CharsetUtil.UTF_8));
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer");
        HttpUtil.setContentLength(response, response.content().readableBytes());
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}