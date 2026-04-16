package com.algomeet.xmpp.chatservice.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class XmppThreadConfig {

    @Bean(name = "pushPresenceExecutor")
    public Executor xmppExecutorPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(15);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("push-presence-proc-");
        // Crucial: How to handle overflow? 
        // CallerRunsPolicy makes the calling thread do the work, naturally slowing down the intake.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Bean(name = "presenceExecutor")
    public Executor presenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core threads: Keep enough to handle normal traffic
        executor.setCorePoolSize(15); 
        // Max threads: Allow expansion during "login storms"
        executor.setMaxPoolSize(50); 
        // Queue: Hold pending broadcasts
        executor.setQueueCapacity(1000); 
        executor.setThreadNamePrefix("presence-proc-");
        // Crucial: How to handle overflow? 
        // CallerRunsPolicy makes the calling thread do the work, naturally slowing down the intake.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}