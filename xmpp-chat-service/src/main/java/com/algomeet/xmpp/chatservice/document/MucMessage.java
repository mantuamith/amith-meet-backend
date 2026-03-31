package com.algomeet.xmpp.chatservice.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Builder
@Document(collection = "muc_messages")
// 1. COMPOUND INDEX for MAM Queries (Room + Sequential ID)
@CompoundIndexes({
    @CompoundIndex(name = "room_id_seq_idx", def = "{'roomId': 1, 'id': 1}")
})
public class MucMessage {    
    @Id
    private String id;           // ULID or Sequential String

    // 2. UNIQUE INDEX for Stanza ID
    // Prevents duplicate messages if a client retries a send
    @Indexed(unique = true)
    private String stanzaId;     
    
    // Indexed via the Compound Index above, but good for simple lookups
    private String roomId;
    
    private String from;
    private String stanzaXml;
    
    private String category;
    
    // 3. INDEX for Threading/Reactions
    // Useful for finding all reactions to a specific message
    @Indexed
    private String refersTo;     
    
    private boolean isE2EE;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
}