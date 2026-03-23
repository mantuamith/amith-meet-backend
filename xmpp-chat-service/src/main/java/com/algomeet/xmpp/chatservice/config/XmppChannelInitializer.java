package com.algomeet.xmpp.chatservice.config;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.DynamicHandshakeHandler;
import com.algomeet.xmpp.chatservice.auth.WebSocketAuthHandler;
import com.algomeet.xmpp.chatservice.routing.XmppRoutingHandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
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
        
        // Step 1: Validate Auth (JWT Check)
        p.addLast(webSocketAuthHandler);
        
        // Step 2: Dynamically configure the WebSocket Handshake based on headers
        // We pass the routing handler so it can be added after handshake
        p.addLast(new DynamicHandshakeHandler(xmppRoutingHandler));
        
        // Step 3: The final processor
        p.addLast(xmppRoutingHandler);
    }
}