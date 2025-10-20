package com.algomeet.chatservice.dto;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the data carried in a group session message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSessionPayload {
    @Field("sessionId")
    private String sessionId;     // Megolm session ID
    
    @Field("sessionKey")
    private String sessionKey;    // Ciphertext - Base64-encoded session key (for SHARE)  
    
    @Field("algorithm")
    private String algorithm;     // e.g. "MEGOLM"    
    
    @Field("ratchetIndex")
    private Integer ratchetIndex; // optional, for synchronization
    
    @Field("signature")
    private String signature;     // optional integrity check
}
