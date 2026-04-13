package com.algomeet.xmpp.chatservice.auth;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.connection.ConnectionLifecycleHandler;
import com.algomeet.xmpp.chatservice.connection.registry.LocalChannelRegistry;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Finalizes the XMPP-over-WebSocket setup immediately after a successful 
 * WebSocket upgrade/handshake.</p>
 * 
 * <p>This handler is responsible for transitioning a connection from a raw 
 * WebSocket to a tracked, authenticated XMPP session. It performs the following 
 * critical bootstrap steps:</p>
 * <ul>
 *     <li>Initializes Stream Management (XEP-0198) counters (Inbound/Outbound 'h').</li>
 *     <li>Registers the session in the {@link LocalChannelRegistry} for routing.</li>
 *     <li>Updates the {@link UserSessionRegistry} to mark the user as ACTIVE.</li>
 *     <li>Initializes the {@link XmppStreamManagementOutboundBuffer} for reliable delivery.</li>
 *     <li>Attaches a lifecycle listener to ensure clean resource teardown on disconnect.</li>
 * </ul>
 * 
 * <p>Note: This handler is {@code @Sharable} as it does not maintain per-channel state; 
 * all state is stored within Channel Attributes or external Managers.</p>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketPostAuthHandler extends ChannelInboundHandlerAdapter {
	private final ConnectionLifecycleHandler userSessionLifecycleHandler;

	/**
	 * Listens for Netty user events, specifically catching {@link WebSocketServerProtocolHandler.HandshakeComplete} 
	 * to trigger the XMPP session activation logic.
	 * 
	 * @param ctx The channel context.
	 * @param evt The event triggered in the pipeline.
	 */
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
			try {
				XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();

				if (principal != null) {
					// Post connection initializations
					userSessionLifecycleHandler.connected(ctx, principal);

					// Setup Lifecycle Cleanup Listener
					// Ensures that if the client disappears, we unregister from all trackers
					ctx.channel().closeFuture().addListener((ChannelFutureListener) future -> {
						userSessionLifecycleHandler.disconnected(ctx, principal);
					});
				} else {
					log.error("WebSocket handshake completed but no XmppPrincipal found. Closing channel.");
					ctx.close();
				}     
			} catch(Exception ex) {
				log.error("WebSocket handshake completed but error encountered during post connection initialization. Closing channel.", ex);
				ctx.close();
			}
		}

		// Pass the event forward in case other handlers need to react
		super.userEventTriggered(ctx, evt);
	} 
}