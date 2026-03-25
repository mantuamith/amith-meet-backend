package com.algomeet.xmpp.chatservice.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
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

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketPreAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final JwtUtil jwtUtil;
    
    @Value("${xmpp.domain}")
    private String domain;
    
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
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
