package com.algomeet.chatservice.document;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;
import lombok.NoArgsConstructor;
/**
* Encryption metadata
*/
@Data
@NoArgsConstructor
public class EncrytionMetadata {
    @Field("isEncrypted")
    private Boolean encrypted = false;  // true if message.content is ciphertext

    // e.g. "SIGNAL"
    @Field("protocol")
    private String protocol; 
    
    // Distribution ID for group messages
    @Field("distributionId")
    private String distributionId; 

    // Receiver deviceId
    @Field("deviceId")
    private String deviceId; 
    
    // Optional: store actual ciphertext separately if you want plaintext 'content' to remain empty
    @Field("ciphertext")
    private String ciphertext;   
    
    // Key material required to decrypt attached media
    private MediaKeyMaterial mediaKeyMaterial;
}
