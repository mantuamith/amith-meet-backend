package com.algomeet.xmpp.chatservice.auth;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import com.algomeet.xmpp.chatservice.routing.XmppRoutingHandler;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DynamicHandshakeHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final XmppRoutingHandler xmppRoutingHandler;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // 1. Extract the subprotocol (the JWT token) from the header
        String subprotocol = req.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
        
        // 2. Build the config dynamically using the token found in THIS specific request
        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/ws/chat")
                .subprotocols(subprotocol) // Agree to the token sent by the client
                .allowExtensions(true)
                .checkStartsWith(true)
                .build();

        // 3. Add the real Protocol Handler now that we know the subprotocol
        ctx.pipeline().addAfter(ctx.name(), "ws-protocol-handler", new WebSocketServerProtocolHandler(config));
        
        // 4. Move the request forward so the new handler can process the handshake
        ctx.fireChannelRead(req.retain());
        
        // 5. Remove this temporary configurator
        ctx.pipeline().remove(this);
    }
}