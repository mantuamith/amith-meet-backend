package com.algomeet.mediaservice.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssConfig {

    @Bean
    public OSS ossClient(StorageProperties props) {
        return new OSSClientBuilder().build(
                props.getOss().getEndpoint(),
                props.getOss().getAccessKeyId(),
                props.getOss().getAccessKeySecret()
        );
    }
}
