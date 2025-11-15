
package com.algomeet.opaqueservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class OpaqueServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpaqueServiceApplication.class, args);
    }
}
