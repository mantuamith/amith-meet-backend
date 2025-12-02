package com.algomeet.signalservice.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "message_backups")
@CompoundIndexes({
        // For queries: {userKey: A, senderKey: B} sorted by timestamp/_id
        @CompoundIndex(name = "idx_user_key_sender_key_ts_id",
                def  = "{'userKey': 1, 'senderKey': 1, 'timestamp': -1, '_id': -1}")
})
public class MessageBackupDocument {
	@Id
    @Size(max = 56)
    private String messageId;
	
	@Deprecated
    @Size(max = 45)
    @Field("userKey")
    private String userKey;   
    
	@NotEmpty
    @Size(max = 45)
    @Field("senderKey")
    private String senderKey; 
    
	@NotEmpty
    @Size(max = 45)
    @Field("receiverKey")
    private String receiverKey;     
    
	@NotEmpty
    @Size(max = 20000)
    @Field("encryptedMessage")
	@Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "{invalid-base64-format}"
		)
    private String encryptedMessage;    
    
    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Size(max = 32)
    @Field("algorithm")
    private String algorithm;
    
	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    @Size(max = 10)
    @Field("version")
    private String version;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    @Pattern(
        regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
        message = "Invalid base64 format"
    )
    @Size(max = 88)
    @Field("salt")
    private String salt;
            
    private Instant timestamp = Instant.now();
}
