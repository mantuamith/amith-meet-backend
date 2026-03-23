package com.algomeet.xmpp.chatservice.config;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NettyConfig {

    @Bean(destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup bossGroup() {
        return new NioEventLoopGroup(1);
    }

    @Bean(destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup workerGroup() {
        return new NioEventLoopGroup();
    }

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