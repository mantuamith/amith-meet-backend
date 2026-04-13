package com.algomeet.xmpp.chatservice.config;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.WebSocketPostAuthHandler;
import com.algomeet.xmpp.chatservice.auth.WebSocketPreAuthHandler;
import com.algomeet.xmpp.chatservice.routing.XmppRoutingHandler;
import com.algomeet.xmpp.chatservice.sm.XmppStreamManagementHandler;

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

    /**
     * Final handler that processes XMPP stanzas after WebSocket upgrade
     */
    private XmppRoutingHandler xmppRoutingHandler;

    /**
     * Handles HTTP request BEFORE WebSocket handshake:
     * - Extracts JWT token
     * - Validates authentication
     * - Stores user principal in channel attributes
     */
    private WebSocketPreAuthHandler webSocketPreAuthHandler;

    /**
     * Handles events AFTER WebSocket handshake:
     * - Listens for HandshakeComplete event
     * - Registers user session
     * - Sends XMPP bind response
     */
    private WebSocketPostAuthHandler webSocketPostAuthHandler;
    
    private XmppStreamManagementHandler xmppStreamManagementHandler;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        /**
         * Step 1: Decode/encode HTTP requests and responses
         * Required for initial WebSocket HTTP upgrade request
         */
        p.addLast(new HttpServerCodec());

        /**
         * Step 2: Aggregate HTTP message fragments into FullHttpRequest
         * Needed because WebSocket handshake requires a full HTTP request
         */
        p.addLast(new HttpObjectAggregator(65536));

        /**
         * Step 3: Pre-authentication (HTTP phase)
         * - Intercepts FullHttpRequest before WebSocket upgrade
         * - Validates JWT token
         * - Rejects unauthorized requests early (before upgrade)
         */
        p.addLast(webSocketPreAuthHandler);

        /**
         * Step 4: WebSocket protocol handler
         * - Handles HTTP → WebSocket upgrade
         * - Validates WebSocket path (/ws/chat)
         * - Negotiates subprotocol ("xmpp")
         * - Fires HandshakeComplete event after successful upgrade
         */
        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
            .websocketPath("/ws/chat")
            .subprotocols("xmpp") // important for XMPP over WebSocket
            .allowExtensions(true)
            .checkStartsWith(true)
            .dropPongFrames(true)
            .build();

        p.addLast(new WebSocketServerProtocolHandler(config));

        /**
         * Step 5: Post-authentication (WebSocket phase)
         * - Triggered by HandshakeComplete event
         * - Registers session ONLY after successful upgrade
         * - Initializes XMPP session state (e.g., stream management)
         * - Sends initial XMPP bind response
         */
        p.addLast(webSocketPostAuthHandler);
        
        
		/**
		 * Step 6: XEP-0198 Stream Management handler Responsible for tracking inbound stanzas
		 * at transport level and maintaining the SM counter (h value) for cumulative
		 * acknowledgements (<a h='N'/>).
		 * 
		 * IMPORTANT: - Must be placed BEFORE business logic handlers (chat, routing,
		 * persistence) - Only counts XMPP stanzas (<message/>, <iq/>, <presence/>) -
		 * Does NOT depend on DB, routing, or delivery success - Ensures reliable
		 * transport-level session recovery (resume support)
		 */         
        p.addLast("smHandler", xmppStreamManagementHandler); 

        /**
         * Step 7: XMPP routing handler
         * - Processes incoming WebSocket frames (XMPP stanzas)
         * - Routes messages to appropriate services (chat, presence, etc.)
         */
        p.addLast(xmppRoutingHandler);
    }
}