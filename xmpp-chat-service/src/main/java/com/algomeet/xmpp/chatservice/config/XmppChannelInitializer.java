package com.algomeet.xmpp.chatservice.config;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.WebSocketAuthHandler;
import com.algomeet.xmpp.chatservice.routing.XmppRoutingHandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class XmppChannelInitializer extends ChannelInitializer<SocketChannel> {	
	private WebSocketAuthHandler webSocketAuthHandler;
    private XmppRoutingHandler xmppRoutingHandler;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(65536));
        p.addLast(webSocketAuthHandler);
        
        // 1. WebSocket Protocol Handler goes first (it will pass the HTTP request through)
        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
            .websocketPath("/ws/chat")
            .subprotocols("xmpp")
            .allowExtensions(true)
            .checkStartsWith(true)
            .dropPongFrames(true)
            .build();
        p.addLast(new WebSocketServerProtocolHandler(config));

        // 2. Auth Handler goes AFTER the protocol handler to catch the event
        p.addLast(webSocketAuthHandler);

        // 3. Final Routing
        p.addLast(xmppRoutingHandler);
    }
}