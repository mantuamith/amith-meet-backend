package com.algomeet.mediaservice.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.AllArgsConstructor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@AllArgsConstructor
public class S3Config {
    private final StorageProperties props;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.getS3().getRegion()));

        if (StringUtils.hasText(props.getS3().getEndpoint())) {
            builder.endpointOverride(URI.create(props.getS3().getEndpoint()))
                   .serviceConfiguration(
                       S3Configuration.builder()
                           .pathStyleAccessEnabled(props.getS3().isPathStyleAccess())
                           .build()
                   );
        }

        return builder.build();
    }
}
