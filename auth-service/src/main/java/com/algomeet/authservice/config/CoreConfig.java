package com.algomeet.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.CommandLineRunner;


import java.time.Clock;

@Configuration
public class CoreConfig {



    @Bean
    CommandLineRunner printBcrypt(PasswordEncoder pe) {
        return args -> {
            String raw = "test@123";
            String hash = pe.encode(raw);
            System.out.println("==== TEST HASH FOR 'test@123' ====");
            System.out.println(hash);
            System.out.println("===================================");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
