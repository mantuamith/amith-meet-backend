package com.algomeet.xmpp.chatservice.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.session.XmppSessionManager;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.CharsetUtil;
import com.algomeet.xmpp.chatservice.util.JwtUtil;
import io.netty.channel.ChannelHandler;

@Component
@ChannelHandler.Sharable
public class WebSocketAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	//@Autowired
	//private JwtUtil jwtUtil;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
		String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);

		if (!(StringUtils.hasText(authHeader))) {
			authHeader = request.headers().get(Constants.SEC_WEBSOCKET_PROTOCOL);
			//authHeader = protocols.get(0);
		}

		System.out.println("---------------------> !!!!!!!!!!!!!!!" + authHeader);
		/*
		if (authHeader == null || !authHeader.startsWith("Bearer")) {
			sendUnauthorized(ctx, request);
			return;
		}

		String token = authHeader.substring(7);

		// TODO: Validate token (JWT validation, call auth service, etc.)
		boolean isValid = validateToken(token);

		if (!isValid) {
			sendUnauthorized(ctx, request);
			return;
		}

		// Optional: store token/user info in channel attributes
		ctx.channel().attr(AuthAttributes.USER_TOKEN).set(token);
        */
		
		if (!request.decoderResult().isSuccess() || !"websocket".equalsIgnoreCase(request.headers().get("Upgrade"))) {
            // Not a websocket handshake, pass it along
            ctx.fireChannelRead(request.retain());
            return;
        }
		
		// Pass request to next handler (WebSocket handshake)
		ctx.fireChannelRead(request.retain());
		
		
	}
	
	private void handleHandshake(ChannelHandlerContext ctx, FullHttpRequest request, String subprotocol) {
	    //String location = "ws://" + request.headers().get(HttpHeaderNames.HOST) + request.uri();
	    WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
	            null, subprotocol, true);
	    
	    WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(request);

	    if (handshaker == null) {
	        WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
	    } else {
	        handshaker.handshake(ctx.channel(), request).addListener(future -> {
	            if (future.isSuccess()) {
	                // --- CRITICAL FIX START ---
	                
	                // 1. Remove the Auth handler (HTTP)
	                ctx.pipeline().remove(this);
	                
	                // 2. IMPORTANT: If you have a 'WebSocketServerProtocolHandler' 
	                // in your initial pipeline, you MUST remove it now because 
	                // you just did the handshake manually.
	                if (ctx.pipeline().get(WebSocketServerProtocolHandler.class) != null) {
	                    ctx.pipeline().remove(WebSocketServerProtocolHandler.class);
	                }

	                // 3. Register user
	                String username = ctx.channel().attr(AuthAttributes.USERNAME).get();
	                XmppSessionManager.register(username, ctx.channel());
	                
	                // --- CRITICAL FIX END ---
	            } else {
	                ctx.close();
	            }
	        });
	    }
	}

	/*
	private boolean validateToken(String token) {
		// Implement JWT verification or call your auth service  	
		return jwtUtil.validate(token);
	}*/

	private void sendUnauthorized(ChannelHandlerContext ctx, FullHttpRequest request) {
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,
				HttpResponseStatus.UNAUTHORIZED
				);

		response.content().writeCharSequence("Unauthorized", CharsetUtil.UTF_8);
		HttpUtil.setContentLength(response, response.content().readableBytes());

		ctx.writeAndFlush(response);
		ctx.close();
	}
}