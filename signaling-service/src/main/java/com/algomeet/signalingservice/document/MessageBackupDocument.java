package com.algomeet.signalingservice.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
    private String messageId;
	
    @Field("userKey")
    private String userKey;   
    
    @Field("senderKey")
    private String senderKey; 
    
    @Field("encryptedMessage")
    private String encryptedMessage;    
        
    private Instant timestamp = Instant.now();
}
