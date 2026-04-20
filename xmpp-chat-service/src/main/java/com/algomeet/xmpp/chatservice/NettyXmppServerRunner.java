package com.algomeet.xmpp.chatservice;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NettyXmppServerRunner implements CommandLineRunner {

    private final ServerBootstrap serverBootstrap;
    private ChannelFuture channelFuture;

    @Value("${xmpp.server.port:8098}")
    private int port;

    public NettyXmppServerRunner(ServerBootstrap serverBootstrap) {
        this.serverBootstrap = serverBootstrap;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting Netty XMPP Server on ws://localhost:" + port + "/ws/chat");
        this.channelFuture = serverBootstrap.bind(port).sync();
    }

    @PreDestroy
    public void stop() {
        if (channelFuture != null) {
            channelFuture.channel().close().awaitUninterruptibly();
        }
    }
}