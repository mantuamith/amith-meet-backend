package com.algomeet.mediaservice.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import com.algomeet.mediaservice.config.ProsodyJwtProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProsodyPublicKeyLoader {

    private final ProsodyJwtProperties properties;
    private volatile PublicKey publicKey;

    @PostConstruct
    void load() {
        if (!properties.isEnabled()) {
            log.info("Prosody file-sharing JWT verification is disabled");
            return;
        }
        try {
            String pem = Files.readString(Path.of(properties.getPublicKeyPath()));
            String base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
            log.info("Loaded Prosody file-sharing JWT public key from {}", properties.getPublicKeyPath());
        } catch (Exception ex) {
            log.warn("Could not load Prosody file-sharing JWT public key from {}: {}. "
                    + "Prosody-issued file-sharing tokens will be rejected until this is fixed.",
                    properties.getPublicKeyPath(), ex.getMessage());
        }
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}
