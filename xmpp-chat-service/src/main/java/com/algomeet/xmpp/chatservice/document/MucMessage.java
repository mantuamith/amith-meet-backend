package com.algomeet.xmpp.chatservice.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Builder
@Document(collection = "muc_messages")
public class MucMessage {
    @Id
    private String id;           // Sequential MAM ID (Server Generated)
    private String stanzaId;     // The original XMPP 'id' attribute
    private String roomId;
    private String from;
    private String stanzaXml;    // The raw XML event
    
    // Metadata for the Client to process the stream
    private String category;     // "chat", "reaction", "retraction"
    private String refersTo;     // The stanzaId this event targets
    private boolean isE2EE;      // Encryption flag
    
    @Builder.Default
    private Instant createdAt = Instant.now();
}