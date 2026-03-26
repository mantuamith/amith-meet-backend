package com.algomeet.xmpp.chatservice.config;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>Configuration class for initializing the Netty Networking Infrastructure.</p>
 * * <p>This class sets up the <b>Reactor Pattern</b> threading model used by Netty 
 * to handle asynchronous I/O. It separates the responsibility of accepting new 
 * connections from the responsibility of processing traffic on existing connections.</p>
 * * <p><b>Key Components:</b></p>
 * <ul>
 * <li><b>Boss Group:</b> Handles incoming "Accept" events for new connections.</li>
 * <li><b>Worker Group:</b> Handles the heavy lifting of read/write operations and 
 * pipeline execution for active sessions.</li>
 * <li><b>ServerBootstrap:</b> The helper class that ties the threading model, 
 * channel type, and protocol logic (via {@link XmppChannelInitializer}) together.</li>
 * </ul>
 * * @author Algomeet Core Team
 */
@Configuration
public class NettyConfig {

    /**
     * Creates the Boss EventLoopGroup. 
     * <p>Configured with a single thread since its only job is to accept 
     * new TCP connections and hand them off to the workers.</p>
     * * @return A single-threaded {@link NioEventLoopGroup}.
     */
    @Bean(destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup bossGroup() {
        return new NioEventLoopGroup(1);
    }

    /**
     * Creates the Worker EventLoopGroup.
     * <p>By default, this uses {@code CPU cores * 2} threads to handle 
     * non-blocking I/O tasks for all active WebSocket channels.</p>
     * * @return A multi-threaded {@link NioEventLoopGroup}.
     */
    @Bean(destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup workerGroup() {
        return new NioEventLoopGroup();
    }

    /**
     * Configures the ServerBootstrap which facilitates the creation of the server-side 
     * {@link io.netty.channel.Channel}.
     * * <p><b>Socket Options:</b></p>
     * <ul>
     * <li>{@code SO_BACKLOG}: Sets the maximum queue length for incoming connection 
     * indications (128).</li>
     * <li>{@code SO_KEEPALIVE}: Enables TCP keep-alive to help detect 
     * "half-open" connections where the client has dropped off silently.</li>
     * </ul>
     * * @param bossGroup             The acceptor thread group.
     * @param workerGroup           The I/O processing thread group.
     * @param xmppChannelInitializer The pipeline definition for XMPP over WebSockets.
     * @return A fully configured {@link ServerBootstrap}.
     */
    @Bean
    public ServerBootstrap serverBootstrap(NioEventLoopGroup bossGroup, 
                                           NioEventLoopGroup workerGroup,
                                           XmppChannelInitializer xmppChannelInitializer) {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
         .channel(NioServerSocketChannel.class)
         .childHandler(xmppChannelInitializer)
         .option(ChannelOption.SO_BACKLOG, 128)
         .childOption(ChannelOption.SO_KEEPALIVE, true);
        return b;
    }
}