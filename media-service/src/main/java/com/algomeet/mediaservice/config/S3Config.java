package com.algomeet.mediaservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.AllArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@AllArgsConstructor
@ConditionalOnProperty(name = "storage.active-upload-storage", havingValue = "s3")
public class S3Config {
    private final StorageProperties props;

//    @Bean
//    public S3Client s3Client() {
//        S3ClientBuilder builder = S3Client.builder()
//                .region(Region.of(props.getS3().getRegion()));
//
//        if (StringUtils.hasText(props.getS3().getEndpoint())) {
//            builder.endpointOverride(URI.create(props.getS3().getEndpoint()))
//                   .serviceConfiguration(
//                       S3Configuration.builder()
//                           .pathStyleAccessEnabled(props.getS3().isPathStyleAccess())
//                           .build()
//                   );
//        }
//
//        return builder.build();
//    }
    
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(props.getS3().getRegion()))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getS3().getAccessKey(), props.getS3().getSecretKey())
                    )
                )
                .build();
    }
}
