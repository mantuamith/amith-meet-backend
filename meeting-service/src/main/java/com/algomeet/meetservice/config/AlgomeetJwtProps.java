// com.algomeet.meetservice.config.AlgomeetJwtProps
package com.algomeet.meetservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "algomeet.jwt")
public class AlgomeetJwtProps {
    private String appId;            // aud
    private String issuer;           // iss
    private String sub;              // sub
    private String secretBase64;     // HS256 key (base64)
    private int ttlSeconds = 300;    // exp - now
    private Map<String, Boolean> features = defaultFeatures();

    private static Map<String, Boolean> defaultFeatures() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put("file-upload", true);
        return defaults;
    }
}
