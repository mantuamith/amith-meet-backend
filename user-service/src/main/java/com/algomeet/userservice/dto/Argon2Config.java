package com.algomeet.userservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // <-- ignore any unknown fields in JSON
public class Argon2Config {
    /**
     * Argon2 hashing mode (Argon2i, Argon2d, Argon2id).
     * Recommended Value: "Argon2id"
     */
    private String variant; 
    
    /**
     * Number of iterations or passes (t). Increase to slow down brute force.
     * Recommended Value: 3
     */
    private Integer timeCost;
    
    /**
     * Memory usage in KiB (m). Critical for GPU resistance.
     * Recommended Value: 65536 (64 MiB) or higher
     */
    private Integer memoryCost;
    
    /**
     * Number of parallel threads/lanes (p).
     * Recommended Value: 1 (on servers)
     */
    private Integer parallelism;
    
    /**
     * Length of the final hash output in bytes (l).
     * Recommended Value: 32 (256-bit)
     */
    private Integer outputLength; 
    
    /**
     * Salt used for Argon2 hashing.
     *
     * Recommended:
     *   - Generate a **cryptographically secure random** salt
     *   - Minimum length: **16 bytes** (128 bits)
     *   - Never hard-code or reuse salts across users
     *
     * Note:
     *   - For password hashing, salts do NOT need to be secret.
     *   - Store the salt alongside the hash.
     */
    private String salt;
    
    /**
     * Argon2 algorithm version.
     * Recommended Value: 19
     */
    private Integer version;
}
