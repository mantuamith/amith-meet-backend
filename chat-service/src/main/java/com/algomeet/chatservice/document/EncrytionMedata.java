package com.algomeet.chatservice.document;

import org.springframework.data.mongodb.core.mapping.Field;
/**
* Encryption metadata
*/
public class EncrytionMedata {
    @Field("isEncrypted")
    private Boolean encrypted = false;  // true if message.content is ciphertext

    // e.g. "OLM" (1:1) or "MEGOLM" (group)
    @Field("encryptionAlgorithm")
    private String encryptionAlgorithm; 
    
    // Megolm session ID for group messages, or Olm session ID for 1:1
    @Field("sessionId")
    private String sessionId;    
    
    // Optional: store actual ciphertext separately if you want plaintext 'content' to remain empty
    @Field("ciphertext")
    private String ciphertext;
    
    // Reference to stored Megolm key
    @Field("sessionKeyId")
    private String sessionKeyId;
    
    // Optional integrity tag, e.g., from Olm message JSON
    @Field("mac")
    private String mac;
    
    // Megolm message index (for ordering + replay protection)
    @Field("ratchetIndex")
    private Long ratchetIndex;    
}
